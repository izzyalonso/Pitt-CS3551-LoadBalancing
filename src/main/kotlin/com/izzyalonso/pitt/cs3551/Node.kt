package com.izzyalonso.pitt.cs3551

import com.izzyalonso.pitt.cs3551.annotation.GuardedBy
import com.izzyalonso.pitt.cs3551.annotation.VisibleForInnerAccess
import com.izzyalonso.pitt.cs3551.model.*
import com.izzyalonso.pitt.cs3551.model.commands.BuildHierarchy
import com.izzyalonso.pitt.cs3551.model.notices.NodeOnline
import com.izzyalonso.pitt.cs3551.net.ServerSocketInterface
import com.izzyalonso.pitt.cs3551.net.send
import com.izzyalonso.pitt.cs3551.net.sendAndClose
import com.izzyalonso.pitt.cs3551.net.sendAsync
import com.izzyalonso.pitt.cs3551.util.MappingCollector
import java.lang.Integer.min
import java.net.Socket
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.NoSuchElementException
import kotlin.math.abs

private const val imbalanceThreshold = 0.1

class Node {
    @GuardedBy("this")
    private val queue = LinkedList<Job>()
    private val running = AtomicBoolean(false)
    private val balancing = AtomicBoolean(false)

    private val workLock = Object()

    private lateinit var thisNode: NodeInfo
    // The hierarchy this node is in charge of
    private lateinit var hierarchy: TreeNode

    // Maps a node to a level; this node's children are at level 0
    @GuardedBy("this") // <- also readonly
    private lateinit var nodeLevelMap: Map<NodeInfo, Int>
    // List of levels mapping node to load
    @GuardedBy("this") // <- includes all the maps as well
    private lateinit var levelLoads: List<MutableMap<NodeInfo, Double>>
    // This node's load, good to cache
    private var currentLoad = 0.0

    private lateinit var loadTracker: LoadTracker

    private lateinit var jobCollectors: List<MappingCollector<NodeInfo, List<JobInfo>>>


    fun start() {
        println("Starting node.")
        running.set(true)

        loadTracker = LoadTracker()

        val controllerPort = System.getenv()[NodeController.ENV_CONTROLLER_PORT]?.toInt()
        if (controllerPort == null) {
            println("Environment variable ${NodeController.ENV_CONTROLLER_PORT} needs to be set.")
            return
        }

        ServerSocketInterface(object: ServerSocketInterface.ListenerAdapter() {
            override fun onConnected(port: Int) {
                // It's good to know who we are
                thisNode = NodeInfo.create("localnode", port)

                // Notify the Controller the Node is ready
                // The node's controller will always be in localhost
                send(Message.create(NodeOnline.create(port)), "localhost", controllerPort)
            }

            override fun onMessageReceived(message: Message, socket: Socket) {
                handleMessage(message, socket)
            }

            override fun onDisconnected() {
                running.set(false)
            }
        }).startListeningAsync()

        while (running.get()) {
            // Should prolly make this a lock instead ¯\_(ツ)_/¯
            while (!balancing.get()) {
                try {
                    Thread.sleep(50)
                } catch (Ix: InterruptedException) {
                    // Timeout
                }
            }

            // Dequeue work
            val work: Job?
            synchronized(this) {
                work = queue.removeFirstOrNull()
            }

            // Work or sleep
            if (work == null) {
                loadTracker.startSleep()
                synchronized(workLock) {
                    workLock.wait(250) // Go to sleep
                }
                loadTracker.endSleep()
            } else {
                loadTracker.startWork()
                doWork(work) // Execute the request
                loadTracker.endWork()
            }

            // Get the load over the last two seconds
            currentLoad = loadTracker.getLoad(2)

            val highestLevelLoads = highestLevelChildrenLoads()

            // TODO? Allow non global roots to perform load balancing? This requires to take my already clockwork
            // TODO  synchronization up yet another notch. Not sure I want to do this. A&B territory & very overworked.
            if (hierarchy.parent() == null) {
                if (checkImbalance(highestLevelChildrenLoads())) {
                    // Trigger load balance operation, will take some time
                    val jobsPerNode = collectJobInfos()

                    // Create the transfer containers and calculate average weight
                    val transferContainers = LinkedList<TransferContainer>()
                    jobsPerNode.entries.forEach {
                        transferContainers.add(TransferContainer(it.key, it.value))
                    }
                    val averageWeight = transferContainers.averageWeight()

                    // Sort; we're looking to transfer work from the busiest nodes to the idlest nodes
                    transferContainers.sort()

                    // So here is how this thing works:
                    //  - I have donors and recipients, one cannot be both on a single operation.
                    //  - A donor donates up until it goes below the average weight.
                    //    - Donors over donate, but I'm using a form of locality I just made up to where I assume
                    //      that if a node is already overworked it'll tend to stay overworked. Fingers crossed.
                    //      Of course, some of my tests are gonna skew the distribution on purpose, the validity
                    //      of this principle would need to be proved using real production systems. Moving on.
                    //  - A recipient receives until it goes above the average weight.
                    //    - Recipients over receive. Same reason.
                    //  - Once an agent is selected for donorship or recepientship, it will stay that way
                    //    until it crosses the line.
                    //  - Once an agent crosses the line it is removed from consideration.
                    //  - We should end up with no agents in the list by the end.
                    //    - Disclaimer, I'm not gonna prove this mathematically, I may be wrong.
                    // Let the games begin
                    val doneTransferContainers = mutableListOf<TransferContainer>()
                    while (transferContainers.size > 1) {
                        val donorContainer = transferContainers.first // Pick the busiest processor
                        while (donorContainer.weight() > averageWeight) {
                            val recipientContainer = transferContainers.last
                            val recipientSlack = averageWeight-recipientContainer.weight()
                            val job = donorContainer.getJobJustUnder(recipientSlack)
                            if (job == null) {
                                // Something went wrong, log something
                                doneTransferContainers.add(donorContainer)
                                transferContainers.removeFirst() // But for now just move over to done
                            } else {
                                recipientContainer.assignJob(job)
                                if (recipientContainer.weight() > averageWeight) {
                                    transferContainers.removeLast()
                                    doneTransferContainers.add(recipientContainer)
                                }
                            }
                            if (donorContainer.weight() < averageWeight) {
                                doneTransferContainers.add(donorContainer)
                                transferContainers.removeFirst()
                            }
                        }
                    }

                    // Send moved jobs as tokens down the pipe
                }
            } else {
                // Communicate subtree's load to parent
                val parent = hierarchy.parent()
                val averageLoad = highestLevelLoads.average()
                sendAsync(Message.create(LoadInfo.create(thisNode, averageLoad)), parent.address(), parent.port())
            }
        }

        loadTracker.done()
    }

    private fun doWork(work: Job) = when (work.type()) {
        Job.Type.FIBONACCI -> runFibo(work.input())
        Job.Type.ERATOSTHENES -> runEratosthenes(work.input())
        Job.Type.SQUARE_SUM -> runSquareSum(work.input())
        null -> 0 // Never gonna happen, just keeping the linter happy. Eyeroll.
    }

    private fun runFibo(nth: Int): Int {
        var f1 = 0
        var f2 = 1
        repeat(min(nth-2, 0)) {
            val tmp = f1+f2
            f1 = f2
            f2 = tmp
        }
        return if (nth == 0) {
            f1
        } else {
            f2
        }
    }

    private fun runEratosthenes(nth: Int): Int {
        val field = BooleanArray(nth+1) { true }
        for (i in 2 until nth) {
            if (!field[i]) {
                continue
            }
            for (j in 2*i until nth step i) {
                field[j] = false
            }
        }
        return field[nth].int()
    }

    private fun runSquareSum(nth: Int): Int {
        var result = 0
        repeat(nth) {
            repeat(nth) {
                result++
            }
        }
        return result
    }

    /**
     * Calculates the loads of the owned nodes at the highest possible level in the hierarchy.
     */
    private fun highestLevelChildrenLoads(): List<Double> {
        var averageLevelLoad = currentLoad
        val lastLevelLoads = mutableListOf<Double>()
        for (i in levelLoads.size-1 downTo 0) {
            lastLevelLoads.clear()
            levelLoads[i].forEach {
                lastLevelLoads.add(it.value)
            }
            lastLevelLoads.add(averageLevelLoad)
            averageLevelLoad = lastLevelLoads.average()
        }
        return lastLevelLoads
    }

    /**
     * Returns true if there is an imbalance, false otherwise.
     */
    private fun checkImbalance(highestLevelChildrenLoads: List<Double>): Boolean {
        val averageLoad = highestLevelChildrenLoads.average()
        for (load in highestLevelChildrenLoads) {
            if (abs(load - averageLoad) > imbalanceThreshold) {
                return true
            }
        }
        return false
    }

    /**
     * Collects this hierarchy's jobs.
     */
    private fun collectJobInfos(): Map<NodeInfo, List<JobInfo>> {
        // It's an unwritten contract that the first node in the list of children is self
        // Cutting corners here, I know I'm going to dev hell
        // Anywho, just need to set up the collectors before I request for thread safety, as I want the
        // operation to be asynchronous for speed. <- Just for reference, Amy, this is what I'm into, parallelization
        val jobCollectors = mutableListOf<MappingCollector<NodeInfo, List<JobInfo>>>()
        var hierarchy = this.hierarchy
        while (!hierarchy.isLeaf) {
            jobCollectors.add(MappingCollector(hierarchy.children().count()))
            hierarchy = hierarchy.children()[0]
        }
        this.jobCollectors = jobCollectors.toList()

        // Request to all hierarchies this node owns
        hierarchy.bfsOnOwned { treeNode, _, _ ->
            val node = treeNode.node()
            if (treeNode.node() == thisNode) {
                return@bfsOnOwned // We don't request ourselves, that'd be silly :)
            }
            send(Message.createCollectJobs(), node.address(), node.port())
        }

        var myJobs = synchronized(this) {
            queue.map { job -> job.getInfo(thisNode) }
        }
        // The top index is a special case, this function does not merge those
        for (i in this.jobCollectors.indices.minus(0).reversed()) {
            this.jobCollectors[i].add(thisNode, myJobs)
            myJobs = this.jobCollectors[i].awaitAndGet().collect()
        }

        this.jobCollectors[0].add(thisNode, myJobs)
        return this.jobCollectors[0].awaitAndGet()
    }

    @VisibleForInnerAccess
    internal fun handleMessage(message: Message, socket: Socket) {
        message.buildHierarchy()?.let { request ->
            hierarchy = buildHierarchy(request)

            buildInternalMappings(hierarchy)
            // Let other nodes know
            communicateHierarchy(hierarchy)

            socket.sendAndClose(Message.create(hierarchy))
        }

        message.hierarchy()?.let { hierarchy ->
            this.hierarchy = hierarchy
            buildInternalMappings(hierarchy)
            communicateHierarchy(hierarchy)
        }

        message.doWork()?.let { request ->
            synchronized(this) {
                queue.add(request)
            }
            synchronized(workLock) {
                workLock.notify()
            }
        }

        message.loadInfo()?.let { request ->
            val node = request.node()
            synchronized(this) {
                nodeLevelMap[node]?.let { level ->
                    levelLoads[level][node] = request.load()
                } // ?: throw(something went wrong)
            }
        }

        if (message.collectJobs()) {
            // This operation is asynchronous and could take time, free up the socket and I'll open a connection later
            socket.close()
            balancing.set(true)

            // This request will only come from the parent
            send(
                Message.create(JobInfoList.create(thisNode, collectJobInfos().collect())),
                hierarchy.parent().address(),
                hierarchy.parent().port()
            )
        }

        message.jobInfoList()?.let {
            jobCollectors[nodeLevelMap[it.sender()] ?: error("something went south")].add(it.sender(), it.jobInfoList())
        }
    }

    /**
     * A bit haphazard IMO. Could be better, but works.
     */
    fun buildHierarchy(request: BuildHierarchy): TreeNode {
        // Create a list of leaves
        val leaves = LinkedList<TreeNode>()
        request.nodes().forEach {
            leaves.add(TreeNode.create(it))
        }

        // First leaf will be the root
        val root = leaves.remove()
        val parents = LinkedList<TreeNode>()
        parents.add(root)

        val extraChildren = request.branchingFactor()-1

        while (leaves.isNotEmpty()) {
            val parent = parents.remove()
            parent.children().add(TreeNode.create(parent.node()))
            parents.add(parent.children()[0]) // We can probably be smarter about this, comes a point when we don't need to add more
            repeat(min(extraChildren, leaves.size)) {
                val nextLeaf = leaves.remove().assignParent(parent.node())
                // Exploiting the fact that a member of an autovalue class is not mutable
                // Really shouldn't be doing this, but who cares
                parent.children().add(nextLeaf)
                parents.add(nextLeaf)
            }
        }

        return root
    }

    /**
     * Looks much like [communicateHierarchy], just want to populate indices and mappings before communicating
     * externally.
     */
    private fun buildInternalMappings(hierarchy: TreeNode) {
        val nodeLevelMap = mutableMapOf<NodeInfo, Int>()
        val levelLoads = mutableListOf<MutableMap<NodeInfo, Double>>()
        hierarchy.bfsOnOwned { treeNode, level, levelChanged ->
            if (levelChanged) {
                levelLoads.add(mutableMapOf())
            }
            val node = treeNode.node()
            if (node != thisNode) {
                nodeLevelMap[node] = level
                levelLoads[level][node] = 0.0
            }
        }
        synchronized(this) {
            this.nodeLevelMap = nodeLevelMap.toMap()
            this.levelLoads = levelLoads.toList()
        }
    }

    /**
     * Communicates the hierarchy to all the nodes this node is in charge of.
     */
    private fun communicateHierarchy(hierarchy: TreeNode) {
        hierarchy.bfsOnOwned { treeNode, _, _ ->
            val node = treeNode.node()
            if (node != thisNode) {
                sendAsync(Message.create(treeNode), node.address(), node.port())
            }
        }
    }

    /**
     * Dequeues an item but returns null instead of throwing if there ain't any.
     *
     * NOTE: not thread safe.
     */
    private fun <T>LinkedList<T>.removeFirstOrNull(): T? {
        return try {
            removeFirst()
        } catch (nsex: NoSuchElementException) {
            null
        }
    }

    /**
     * true -> 1
     * false -> 0
     */
    private fun Boolean.int() = if (this) {
        1
    } else {
        0
    }

    /**
     * Walks a hierarchy starting at the node being called using breadth first search. Only delivers nodes
     * this node is actually in charge of. Along with the node, it delivers the level and whether the level
     * changed in the last iteration.
     */
    private fun TreeNode.bfsOnOwned(action: (treeNode: TreeNode, level: Int, levelChanged: Boolean) -> Unit) {
        var currentNode = this
        var level = 0
        // Until we get to the very bottom
        while (!currentNode.isLeaf) {
            // Signal this is a new level
            var levelChanged = true
            // For every node
            currentNode.children().forEach {
                // replace current if self
                if (it == thisNode) {
                    currentNode = it
                }
                // Call action and reset level change to false
                action(it, level, levelChanged)
                levelChanged = false
            }
            // Increment tracked level
            level++
        }
    }

    /**
     * Goodness... I <3 Kt...
     */
    private fun <K, V>Map<K, List<V>>.collect(): MutableList<V> = values.fold(mutableListOf()) { acc, values -> acc.apply { addAll(values) } }

    private fun List<TransferContainer>.averageWeight(): Int {
        var weight = 0
        forEach {
            weight += it.weight()
        }
        return weight/size
    }
}

/**
 * Just a containerized way of keeping track of transferred weights.
 */
class TransferContainer(val node: NodeInfo, jobs: List<JobInfo>): Comparable<TransferContainer> {
    private val added = mutableListOf<JobInfo>()

    private val jobsBySize = mutableListOf<JobInfo?>()
    var totalRemaining: Int = 0
    private set
    var totalAdded: Int = 0
    private set

    init {
        jobs.sorted().forEach {
            jobsBySize.add(it)
            totalRemaining += it.weight()
        }
    }

    /**
     * Just a note. May return the job just above if the job just under does not exist. May return null if
     * neither actually exist. That last scenario would be a bug though ¯\_(ツ)_/¯
     */
    fun getJobJustUnder(size: Int): JobInfo? {
        if (jobsBySize.isEmpty()) {
            return null
        }

        var i = 0
        for (j in jobsBySize.indices) {
            i = j
            if (jobsBySize[j].weightOrMax() < size) {
                break
            }
        }

        val tmp = jobsBySize[i]
        // Not worth removing, too expensive
        jobsBySize[i] = null
        tmp?.let {
            totalRemaining -= it.weight()
        }
        return tmp
    }

    fun assignJob(job: JobInfo) {
        added.add(job)
        totalAdded += job.weight()
    }

    fun weight() = totalRemaining + totalAdded

    // Reversed cause descending
    override fun compareTo(other: TransferContainer) = other.weight().compareTo(weight())

    private fun JobInfo?.weightOrMax() = this?.weight() ?: Int.MAX_VALUE
}


fun main() {
    val nodes = mutableListOf<NodeInfo>()
    for (i in 0 until 4) {
        nodes.add((NodeInfo.create("node", i)))
    }

    println(Node().buildHierarchy(BuildHierarchy.create(2, nodes)).toJson())
}
