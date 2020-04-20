package com.izzyalonso.pitt.cs3551

import com.izzyalonso.pitt.cs3551.annotation.GuardedBy
import com.izzyalonso.pitt.cs3551.annotation.VisibleForInnerAccess
import com.izzyalonso.pitt.cs3551.model.*
import com.izzyalonso.pitt.cs3551.model.commands.BuildHierarchy
import com.izzyalonso.pitt.cs3551.model.notices.NodeOnline
import com.izzyalonso.pitt.cs3551.net.*
import com.izzyalonso.pitt.cs3551.util.ConditionLock
import com.izzyalonso.pitt.cs3551.util.Logger
import com.izzyalonso.pitt.cs3551.util.MappingCollector
import java.lang.Integer.min
import java.net.Socket
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.NoSuchElementException
import kotlin.math.abs
import kotlin.math.max

private const val imbalanceThreshold = 0.1

class Node {
    @GuardedBy(who = "this")
    private val queue = LinkedList<Job>()

    private val running = AtomicBoolean(false)
    private val hierarchySet = AtomicBoolean(false)
    private val balancing = AtomicBoolean(false)
    private val lastBalanceOp = AtomicLong(0L)
    private val operationsToResume = AtomicInteger()

    private val workLock = Object()

    lateinit var thisNode: NodeInfo
    // The hierarchy this node is in charge of
    private lateinit var hierarchy: TreeNode

    // Maps a node to a level; this node's children are at level 0
    @GuardedBy(who = "this") // <- also readonly
    private lateinit var nodeLevelMap: Map<NodeInfo, Int>
    // List of levels mapping node to load
    @GuardedBy(who = "this") // <- includes all the maps as well
    private lateinit var levelLoads: List<MutableMap<NodeInfo, Double>>
    // This node's load, good to cache
    private var currentLoad = 0.0

    private lateinit var loadTracker: LoadTracker

    private lateinit var jobCollectors: List<MappingCollector<NodeInfo, List<JobInfo>>>
    // Metadata to be able to make decisions on how to move jobs around
    private lateinit var jobNodeMapping: List<MutableMap<JobInfo, NodeInfo>>


    fun start() {
        try {
            startInternal()
        } catch (x: Exception) {
            sendLogSync("Some node failed: $x")
            for (line in x.stackTrace) {
                sendLogSync("$line")
            }
            Thread.sleep(2000)
        }
        sendLogSync("WE GOT SOME FAILURE HERE. $thisNode")
    }

    private fun startInternal() {
        sendLog("Starting node.")
        running.set(true)

        loadTracker = LoadTracker()

        val controllerPort = System.getenv()[NodeController.ENV_CONTROLLER_PORT]?.toInt()
        val nodeId = System.getenv()[NodeController.ENV_NODE_ID]?.toInt() ?: -1
        if (controllerPort == null) {
            println("Environment variable ${NodeController.ENV_CONTROLLER_PORT} needs to be set.")
        }

        sendLog("Controller at $controllerPort")

        val connectedLatch = CountDownLatch(1)
        ServerSocketInterface(object: ServerSocketInterface.ListenerAdapter() {
            override fun onConnected(port: Int) {
                // It's good to know who we are
                thisNode = NodeInfo.create(nodeId, "localhost", port)

                Logger.i("Node connected at $thisNode")
                sendLog("Node connected at $thisNode")

                // Notify the Controller the Node is ready
                // The node's controller will always be in localhost
                if (controllerPort != null) {
                    sendAsync(Message.create(NodeOnline.create(nodeId, port)), "localhost", controllerPort)
                }
                connectedLatch.countDown()
            }

            override fun onMessageReceived(message: Message, socket: Socket) {
                handleMessage(message, socket)
            }

            override fun onDisconnected() {
                running.set(false)
            }
        }).startListeningAsync()

        // Wait til we're connected
        connectedLatch.await()
        // And wait until we got a hierarchy
        sendLog("$thisNode waiting for hierarchy")
        ConditionLock {
            hierarchySet.get()
        }.await()
        sendLog("$thisNode has a hierarchy: $hierarchy")

        lastBalanceOp.set(System.currentTimeMillis())

        while (running.get()) {
            //sendLog("$thisNode is running, queue size: ${queue.size}")
            // Should prolly make this a monitored lock instead ¯\_(ツ)_/¯
            // Oh well, worst case scenario we lose 50 ms
            while (balancing.get()) {
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
                //sendLog("$thisNode is going to sleep")
                loadTracker.startSleep()
                synchronized(workLock) {
                    workLock.wait(250) // Go to sleep
                }
                loadTracker.endSleep()
                //sendLog("$thisNode woke up")
            } else {
                loadTracker.startWork()
                doWork(work) // Execute the request
                loadTracker.endWork()
                //sendLog("$thisNode completed $work")
            }

            // Get the load over the last two seconds
            currentLoad = loadTracker.getLoad(2)
            //sendLog("$thisNode current load: $currentLoad")

            val highestLevelLoads = highestLevelChildrenLoads()

            // TODO? Allow non global roots to perform load balancing without trigger a full scale operation?
            // TODO  This requires to take my already clockwork synchronization up yet another notch. Not sure
            // TODO  I want to do this. A&B territory & very overworked.
            if (hierarchy.parent() == null) {
                //sendLog("Level loads: $levelLoads")
                //sendLog("Checking balance...")
                if (checkImbalance(highestLevelLoads)) {
                    sendLog("Triggering load balancing operation")

                    // Gather information about the state of the cluster, will take some time
                    val jobsPerNode = collectJobInfos()
                    sendLog("Collected jobs: $jobsPerNode")

                    // Create the transfer containers
                    val transferContainers = LinkedList<TransferContainer>()
                    hierarchy.children().forEach {
                        val node = it.node()
                        transferContainers.add(TransferContainer(node, jobsPerNode[node] ?: listOf()))
                    }
                    sendLog("Transfers containers created")

                    // Execute the operation
                    val transfers = loadBalance(transferContainers)
                    sendLog("Transfers: $transfers")
                    // Classify the transfers and send the results to the relevant nodes
                    val transfersPerNode = mutableMapOf<NodeInfo, MutableList<JobTransfer>>()
                    for (node in jobsPerNode.keys) {
                        transfersPerNode[node] = mutableListOf()
                    }
                    for (transfer in transfers) {
                        transfersPerNode[transfer.donor()]?.add(transfer)
                        transfersPerNode[transfer.recipient()]?.add(transfer)
                    }

                    transfersPerNode.forEach {
                        sendLog("$it")
                    }

                    transfersPerNode.forEach { (node, transfers) ->
                        if (node != thisNode) {
                            send(Message.create(LoadBalancingResult.create(transfers)), node.address(), node.port())
                        }
                    }
                    propagatedLoadBalancingOperation(transfersPerNode[thisNode]!!, 1)
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
        repeat(max(nth-2, 0)) {
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
        Thread.sleep(nth.toLong())
        return 0
        /*var result = 0
        repeat(nth) {
            repeat(nth) {
                result++
            }
        }
        return result*/
    }

    /**
     * Calculates the loads of the owned nodes at the highest possible level in the hierarchy.
     */
    private fun highestLevelChildrenLoads(): List<Double> {
        var averageLevelLoad = currentLoad
        val lastLevelLoads = mutableListOf(averageLevelLoad)
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
        if (lastBalanceOp.get() > System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(5)) {
            //sendLog("Too soon since the last balancing operation")
            return false
        }
        val averageLoad = highestLevelChildrenLoads.average()
        sendLog("Checking balance: $averageLoad, $highestLevelChildrenLoads")
        for (load in highestLevelChildrenLoads) {
            if (abs(load - averageLoad) > imbalanceThreshold) {
                return true
            }
        }
        return false
    }

    /**
     * Collects this hierarchy's jobs. TODO here
     */
    private fun collectJobInfos(): Map<NodeInfo, List<JobInfo>> {
        sendLog("Collecting Job Infos")
        if (hierarchy.isLeaf) {
            sendLog("$thisNode is leaf, shortcutting.")
            val jerbs = synchronized(this) {
                queue.map { it.getInfo(thisNode) }
            }
            return mapOf(Pair(thisNode, jerbs))
        }

        // It's an unwritten contract that the first node in the list of children is self
        // Cutting corners here, I know I'm going to dev hell
        // Anywho, just need to set up the collectors before I request for thread safety, as I want the
        // operation to be asynchronous for speed. <- Just for reference, Amy, this is what I'm into, parallelization
        val jobCollectors = mutableListOf<MappingCollector<NodeInfo, List<JobInfo>>>()
        val jobNodeMapping = mutableListOf<MutableMap<JobInfo, NodeInfo>>()
        var tempHierarchy = this.hierarchy
        while (!tempHierarchy.isLeaf) {
            jobCollectors.add(MappingCollector(tempHierarchy.children().count()))
            jobNodeMapping.add(mutableMapOf())
            tempHierarchy = tempHierarchy.children()[0]
        }
        this.jobCollectors = jobCollectors.toList()
        this.jobNodeMapping = jobNodeMapping.toList()


        // Request to all hierarchies this node owns
        this.hierarchy.bfsOnOwned { treeNode, _, _ ->
            val node = treeNode.node()
            if (node != thisNode) { // We don't request ourselves, that'd be silly :)
                sendLog("Requesting to $node")
                sendAsync(Message.createCollectJobs(), node.address(), node.port())
            }
        }

        var myJobs = synchronized(this) {
            queue.map { job -> job.getInfo(thisNode) }
        }
        // The top index is a special case, this function does not merge those
        for (i in this.jobCollectors.indices.minus(0).reversed()) {
            this.jobCollectors[i].add(thisNode, myJobs)
            val nodesToJobs = this.jobCollectors[i].awaitAndGet()
            nodesToJobs.forEach { nodeToJobs ->
                nodeToJobs.value.forEach { job ->
                    this.jobNodeMapping[i][job] = nodeToJobs.key
                }
            }
            myJobs = this.jobCollectors[i].awaitAndGet().collect()
        }

        // Not DRY >:, but also ¯\_(ツ)_/¯
        this.jobCollectors[0].add(thisNode, myJobs)
        val nodesToJobs = this.jobCollectors[0].awaitAndGet()
        nodesToJobs.forEach { nodeToJobs ->
            nodeToJobs.value.forEach { job ->
                this.jobNodeMapping[0][job] = nodeToJobs.key
            }
        }
        return nodesToJobs
    }

    /**
     * Load balance. This is where the secret sauce is. Most of it anywho.
     */
    fun loadBalance(transferContainers: LinkedList<TransferContainer>): MutableList<JobTransfer> {
        // Create the transfer containers and calculate average weight
        val averageWeight = transferContainers.averageWeight()
        sendLog("Average weight: $averageWeight")

        // Sort; we're looking to transfer work from the busiest nodes to the idlest nodes
        transferContainers.sort()

        // In the end, all we care about is the transfers
        val jobTransfers = mutableListOf<JobTransfer>()

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
        // Let the games begin
        val doneTransferContainers = mutableListOf<TransferContainer>()
        while (transfersAvailable(transferContainers, averageWeight)) {
            val donorContainer = transferContainers.first // Pick the busiest processor
            while (donorContainer.weight() > averageWeight) {
                sendLog("Donor Weight: ${donorContainer.weight()}")
                val recipientContainer = transferContainers.last
                val recipientSlack = averageWeight-recipientContainer.weight()
                sendLog("Recipient's Slack: $recipientSlack")
                val job = donorContainer.getJobJustUnder(recipientSlack)
                sendLog("Transferring job $job")
                if (job == null) {
                    // Something went wrong, log something
                    doneTransferContainers.add(donorContainer)
                    transferContainers.removeFirst() // But for now just move over to done
                } else {
                    // Assign the job to the recipient, record the transfer, and check whether the recipient is done
                    recipientContainer.assignJob(job)
                    jobTransfers.add(JobTransfer.create(job, donorContainer.node, recipientContainer.node))
                    if (recipientContainer.weight() > averageWeight) {
                        transferContainers.removeLast()
                        doneTransferContainers.add(recipientContainer)
                    }
                }
            }
            // Remove the donor from the containers to dispatch.
            doneTransferContainers.add(donorContainer)
            transferContainers.removeFirst()
        }

        return jobTransfers
    }

    private fun transfersAvailable(transferContainers: List<TransferContainer>, averageWeight: Long): Boolean {
        if (transferContainers.isEmpty()) {
            return false
        }

        val first = transferContainers.first().weight() > averageWeight
        val last = transferContainers.last().weight() > averageWeight
        return first != last
    }

    @VisibleForInnerAccess
    internal fun handleMessage(message: Message, socket: Socket) {
        message.buildHierarchy()?.let { request ->
            sendLog("$thisNode is building the hierarchy.")
            hierarchy = buildHierarchyOp(request)
            sendLog("$thisNode built the hierarchy.")
            hierarchySet.set(true)
            socket.send(Message.create(hierarchy))
        }

        message.hierarchy()?.let { hierarchy ->
            sendLog("$thisNode just got its hierarchy: $hierarchy")
            buildInternalMappings(hierarchy)
            communicateHierarchy(hierarchy)
            this.hierarchy = hierarchy
            hierarchySet.set(true)
            socket.close()
        }

        message.doWork()?.let { request ->
            //sendLog("$thisNode got a work request: $request")
            synchronized(this) {
                if (!balancing.get()) {
                    queue.add(request)
                }
            }
            synchronized(workLock) {
                workLock.notify()
            }
            socket.close()
        }

        message.loadInfo()?.let { update ->
            sendLog("$thisNode got a load update: $update")
            val node = update.node()
            synchronized(this) {
                nodeLevelMap[node]?.let { level ->
                    levelLoads[level][node] = update.load()
                } // ?: throw(something went wrong)
            }
        }

        if (message.collectJobs()) {
            sendLog("$thisNode asked to collect jobs")
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
            sendLog("$thisNode just got a job info list: $it")
            jobCollectors[nodeLevelMap[it.sender()] ?: error("something went south")].add(it.sender(), it.jobInfoList())
        }

        // This message is only received at the top level, so we can work backwards down the hierarchy
        message.loadBalancingResult()?.let {
            sendLog("Got my result: $it")
            if (hierarchy.isLeaf) {
                // Find me jobs and get oot of balancing mode
                if (it.jobTransfers().isEmpty()) {
                    balancing.set(false)
                } else {
                    val requestMap = mutableMapOf<NodeInfo, MutableList<JobTransfer>>()
                    val outbound = mutableSetOf<NodeInfo>()
                    it.jobTransfers().forEach { transfer ->
                        if (transfer.donor() == thisNode) {
                            outbound.add(transfer.donor())
                        } else if (transfer.recipient() == thisNode) {
                            requestMap.putIntoList(transfer.donor(), transfer)
                        }
                    }
                    operationsToResume.addAndGet(outbound.size+requestMap.size)

                    requestMap.forEach { (donor, transfers) ->
                        val response = send(Message.createJobTransferRequest(transfers), donor.address(), donor.port())
                        response?.jobs()?.let {
                            synchronized(this) {
                                queue.addAll(it)
                            }
                        }
                        balancing.set(operationsToResume.decrementAndGet() == 0)
                    }
                }
            } else {
                propagatedLoadBalancingOperation(message.loadBalancingResult().jobTransfers(), 0)
            }
        }

        message.jobTransfer()?.let { transferRequest ->
            val jobIdSet = mutableSetOf<Int>()
            transferRequest.forEach {
                jobIdSet.add(it.job().jobId())
            }

            val jobs = synchronized(this) {
                val jobs = mutableListOf<Job>()
                queue.forEach {
                    if (jobIdSet.contains(it.id())) {
                        jobs.add(it)
                    }
                }
                queue.removeAll(jobs) // I don't care about being efficient at 3:51a on a Sunday
                jobs
            }
            socket.send(Message.create(jobs))
            balancing.set(operationsToResume.decrementAndGet() == 0)
        }
    }

    fun buildHierarchyOp(request: BuildHierarchy): TreeNode {
        val hierarchy = buildHierarchy(request)
        sendLog("$thisNode built the hierarchy: $hierarchy")
        buildInternalMappings(hierarchy)
        // Let other nodes know
        communicateHierarchy(hierarchy)

        return hierarchy
    }

    /**
     * A bit haphazard IMO. Could be better, but works.
     */
    private fun buildHierarchy(request: BuildHierarchy): TreeNode {
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
                sendLog("$thisNode is sending hierarchy to $node")
                sendAsync(Message.create(treeNode), node.address(), node.port())
            }
        }
    }

    /**
     * As it propagates down the hierarchy.
     */
    private fun propagatedLoadBalancingOperation(transfers: List<JobTransfer>, round: Int) {
        // Need to factor in transfer decisions into the next round
        // Separate donated from received
        // We need to be able to look up the blacklist quickly
        val jobBlacklistPerNode = mutableMapOf<NodeInfo, MutableSet<JobInfo>>()
        val jobsDonated = mutableListOf<JobTransfer>() // Deaggregated
        // We also need to keep a map of received to redistribute
        val jobsReceived = mutableListOf<JobTransfer>()
        transfers.forEach {
            if (it.donor() == thisNode) {
                val deaggregated = it.replacing(thisNode, jobNodeMapping[round][it.job()]) // And deaggregate while we're at it
                jobsDonated.add(deaggregated)
                if (!jobBlacklistPerNode.containsKey(deaggregated.donor())) {
                    jobBlacklistPerNode[deaggregated.donor()] = mutableSetOf()
                }
                jobBlacklistPerNode[deaggregated.donor()]?.add(deaggregated.job())
            } else if (it.recipient() == thisNode) {
                jobsReceived.add(it)
            }
        }

        // Original jobs per node
        val jobsPerNodeAtCollectTime = jobCollectors[round].awaitAndGet() // <- the condition has already been met, there is no waiting
        // New map we're constructing
        val jobsPerNode = mutableMapOf<NodeInfo, MutableList<JobInfo>>()
        // Remove donated objects (or rather, don't include them)
        jobsPerNodeAtCollectTime.forEach { entry ->
            jobsPerNode[entry.key] = mutableListOf()
            entry.value.forEach {
                if (jobBlacklistPerNode[entry.key]?.contains(it) == true) {
                    jobsPerNode[entry.key]?.add(it)
                }
            }
        }

        // Create transfer containers
        val transferContainers = LinkedList<TransferContainer>()
        jobsPerNode.entries.forEach {
            transferContainers.add(TransferContainer(it.key, it.value))
        }
        transferContainers.sort()

        // Keep track of these as a map, as some may need to not be taken into account
        val reassignedJobs = mutableMapOf<JobInfo, JobTransfer>()
        jobsReceived.forEach { transfer ->
            val target = transferContainers.last
            target.assignJob(transfer.job())
            reassignedJobs[transfer.job()] = transfer.replacing(thisNode, target.node)
            transferContainers.sort() // Won't be a billion containers, just 2 - 4
        }

        val finalPreTransferContainers = LinkedList<TransferContainer>()
        transferContainers.forEach {
            finalPreTransferContainers.add(it.collapsed())
        }

        val levelTransfers = loadBalance(finalPreTransferContainers)

        // If reassigned, remove
        levelTransfers.forEach {
            reassignedJobs.remove(it.job())
        }
        reassignedJobs.values.forEach {
            levelTransfers.add(it)
        }
        levelTransfers.addAll(jobsDonated)

        val transfersPerNode = mutableMapOf<NodeInfo, MutableList<JobTransfer>>()
        for (node in jobsPerNode.keys) {
            transfersPerNode[node] = mutableListOf()
        }
        for (transfer in levelTransfers) {
            transfersPerNode[transfer.donor()]?.add(transfer)
            transfersPerNode[transfer.recipient()]?.add(transfer)
        }

        if (round == jobCollectors.size-1) {
            // Find my jobs
        } else {
            propagatedLoadBalancingOperation(levelTransfers, round+1)
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
                if (it.node() == thisNode) {
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

    private fun List<TransferContainer>.averageWeight(): Long {
        var weight = 0L
        forEach {
            weight += it.weight()
        }
        return weight/size
    }

    private fun <K, V>MutableMap<K, MutableList<V>>.putIntoList(key: K, value: V) {
        if (containsKey(key)) {
            get(key)?.add(value)
        } else {
            put(key, mutableListOf(value))
        }
    }
}

/**
 * Just a containerized way of keeping track of transferred weights. Don't wanna be recomputing anything.
 */
class TransferContainer(val node: NodeInfo, jobs: List<JobInfo>): Comparable<TransferContainer> {
    private val added = mutableListOf<JobInfo>()

    private val jobsBySize = mutableListOf<JobInfo?>()
    var totalRemaining: Long = 0L
    private set
    var totalAdded: Long = 0L
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
    fun getJobJustUnder(size: Long): JobInfo? {
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

        var tmp = jobsBySize[i]
        while (tmp == null && i >= 0) {
            tmp = jobsBySize[--i]
        }
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

    fun getAdded() = added

    fun weight() = totalRemaining + totalAdded

    // Reversed cause descending
    override fun compareTo(other: TransferContainer) = other.weight().compareTo(weight())

    fun collapsed(): TransferContainer {
        val jobs = mutableListOf<JobInfo>()
        for (job in jobsBySize) {
            if (job != null) {
                jobs.add(job)
            }
        }
        jobs.addAll(added)
        return TransferContainer(node, jobs)
    }

    private fun JobInfo?.weightOrMax() = this?.weight() ?: Long.MAX_VALUE
}


fun main() {
    hierarchyTest()
}

fun hierarchyTest() {
    val nodes = mutableListOf<NodeInfo>()
    for (i in 0 until 4) {
        nodes.add((NodeInfo.create("node", i)))
    }

    val node = Node()
    node.thisNode = nodes[0]
    val hierarchy = node.buildHierarchyOp(BuildHierarchy.create(2, nodes))
    println(hierarchy.toJson())
}

fun loadBalanceTest() {
    val jobsPerNode = mutableMapOf<NodeInfo, List<JobInfo>>()
    for (i in 0 until 4) {
        val node = NodeInfo.create("node", i)
        val jobs = mutableListOf<JobInfo>()
        for (j in 0 until i) {
            jobs.add(JobInfo.create(Job.create(Job.Type.SQUARE_SUM, ((j+1)*(i+1))), node))
        }
        jobsPerNode[node] = jobs
    }

    val transferContainers = LinkedList<TransferContainer>()
    jobsPerNode.entries.forEach {
        transferContainers.add(TransferContainer(it.key, it.value))
    }

    val transfers = Node().loadBalance(transferContainers)

    println(jobsPerNode)
    println(transfers)
}
