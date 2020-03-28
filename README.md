# Pitt-CS3551-LoadBalancing

Term project for Distributed Information Systems.

## Architecture

The project has three key components, Nodes, Node Controllers, and a Client, each one
with a distinct set of responsibilities. All communication amongst components happens
over TCP sockets. There is only one exception to this rule: setting up a node requires
an environment variable to be set up, but I'll cover this later. Messages are passed
around in JSON format. The json strings are generated and parsed automatically by the
model, with [Message](src/main/java/com/izzyalonso/pitt/cs3551/model/Message.java) being
the root JSON object.

### Node Controller

A [node controller](src/main/kotlin/com/izzyalonso/pitt/cs3551/NodeController.kt) is a
key architectural component of the system. Whereas this component does not perform any
work related to load balancing, it's useful to set up the project in an infrastructure
with limited resources. The node controller is the entry point to aVM for setup,
teardown, or any other infrastructure management operation. By default, the node
controllers listen to port 13991, but this is configurable for local testing with
multiple controllers. Node controllers don't support interleaved operations for the
time being, one must finish before the next starts. Any command to execute an operation
received while the node is executing another operation will be rejected. Following is a
list of currently supported operations.

#### Spin Up Nodes

Triggered by sending a Message having a
[SpinUpNodes](src/main/java/com/izzyalonso/pitt/cs3551/model/commands/controller/SpinUpNodes.java)
component. The parameter, `nodeCount`, lets the node controller know how many nodes it
should spin up. Each node is started in a separate process to better take advantage of
multiprocessing in machines with multiple CPUs. The controller will set up an environment
variable containing the port it locked when it started so that the newly spawned node
can notify the controller when set up is done. The controller then compiles all the
information it gathered about the nodes, namely the port they locked themselves, and
will notify the client.

#### Kill Nodes

Triggered by sending a Messgae having a
[KillNodes](src/main/java/com/izzyalonso/pitt/cs3551/model/commands/controller/KillNodes.java)
component. This operation will kill all processes running nodes currently being tracked
by the controller.

### Node

A node is a machine that performs work in the system. Details to come. Planned supported
operations are the following. Pending revision.

#### Build Tree

Instructs the node to create the load balancing hierarchy and inform the all other nodes
of the output of the operation.

#### Do work

Queues some work in a node.

#### Get Load / Send Load

A node requesting load intelligence to another node.

#### Request Work / Send Work

It's unclear to me how this is going to look like as of today.

### Client

The client's functionality is to set up the system given a set of node controllers and
to schedule work on the actual nodes. Most likely will look like a CLI receiving input
from a human operator.
