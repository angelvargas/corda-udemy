package net.corda.training.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.training.contract.IOUContract
import net.corda.training.state.IOUState

/**
 * This is the flow which handles issuance of new IOUs on the ledger.
 * Gathering the counterparty's signature is handled by the [CollectSignaturesFlow].
 * Notarisation (if required) and commitment to the ledger is handled by the [FinalityFlow].
 * The flow returns the [SignedTransaction] that was committed to the ledger.
 */
@InitiatingFlow
@StartableByRPC
class IOUIssueFlow(val state: IOUState) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        // A notary [Party] object can be obtained from [FlowLogic.serviceHub.networkMapCache].
        //In this training project there is only one notary
        val notary = serviceHub.networkMapCache.notaryIdentities.first();
        //Create a [TransactionBuilder] and pass it a notary reference.
        val builder = TransactionBuilder(notary = notary);
        //Create an [IOUContract.Commands.Issue] inside a new [Command].
        //The required signers will be the same as the state's participants
        val issueCommand = Command(IOUContract.Commands.Issue(), state.participants.map { it.owningKey });
        //Add the [Command] to the transaction builder [addCommand].
        builder.addCommand(issueCommand);
        //Use the flow's [IOUState] parameter as the output state with [addOutputState]
        builder.addOutputState(state, IOUContract.IOU_CONTRACT_ID);
        //Extra credit: use [TransactionBuilder.withItems] to create the transaction instead
        builder.withItems();
        //TODO: Amend the [IOUIssueFlow] to verify the transaction as well as sign it.
        builder.verify(serviceHub);
        //Sign the transaction and convert it to a [SignedTransaction] using the [serviceHub.signInitialTransaction] method.
        val ptx = serviceHub.signInitialTransaction(builder);
        //Get a set of signers required from the participants who are not the node
        //[ourIdentity] will give you the identity of the node you are operating as
        //Use [initiateFlow] to get a set of [FlowSession] objects
        //Using [state.participants] as a base to determine the sessions needed is recommended. [participants] is on
        val sessions = (state.participants - ourIdentity).map { initiateFlow(it) }.toSet();
        //Use [subFlow] to start the [CollectSignaturesFlow]
        //Pass it a [SignedTransaction] object and [FlowSession] set
        val stx = subFlow( CollectSignaturesFlow(ptx, sessions) );
        //It will return a [SignedTransaction] with all the required signatures
        //The subflow performs the signature checking and transaction verification for you
        //Now we need to store the finished [SignedTransaction] in both counter-party vaults.
        //TODO: Amend the [IOUIssueFlow] by adding a call to [FinalityFlow].
        return subFlow(FinalityFlow(stx, sessions));
    }
}

/**
 * This is the flow which signs IOU issuances.
 * The signing is handled by the [SignTransactionFlow].
 */
@InitiatedBy(IOUIssueFlow::class)
//Create a subclass of [SignTransactionFlow]
class IOUIssueFlowResponder(val flowSession: FlowSession): FlowLogic<SignedTransaction>() {
    ////Override [SignTransactionFlow.checkTransaction] to impose any constraints on the transaction
    @Suspendable
    override fun call(): SignedTransaction {
        val signedTransactionFlow = object : SignTransactionFlow(flowSession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                val output = stx.tx.outputs.single().data
                "This must be an IOU transaction" using (output is IOUState)
            }
        }


        //Using this flow you abstract away all the back-and-forth communication required for parties to sign a transaction.
        val txWeJustSignedId = subFlow(signedTransactionFlow)

        return subFlow(ReceiveFinalityFlow(otherSideSession = flowSession, expectedTxId = txWeJustSignedId.id))
    }
}