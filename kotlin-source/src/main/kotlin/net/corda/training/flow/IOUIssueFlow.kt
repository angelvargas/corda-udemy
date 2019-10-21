package net.corda.training.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.requireThat
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.SignTransactionFlow
import net.corda.core.flows.StartableByRPC
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
        builder.addOutputState(state);
        //Extra credit: use [TransactionBuilder.withItems] to create the transaction instead
        builder.withItems();
        //Sign the transaction and convert it to a [SignedTransaction] using the [serviceHub.signInitialTransaction] method.
        //Return the [SignedTransaction].
        return serviceHub.signInitialTransaction(builder);

        // Placeholder code to avoid type error when running the tests. Remove before starting the flow task!
//        return serviceHub.signInitialTransaction(
//
//                TransactionBuilder(notary = null)
//        )
    }
}

/**
 * This is the flow which signs IOU issuances.
 * The signing is handled by the [SignTransactionFlow].
 */
@InitiatedBy(IOUIssueFlow::class)
class IOUIssueFlowResponder(val flowSession: FlowSession): FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val signedTransactionFlow = object : SignTransactionFlow(flowSession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                val output = stx.tx.outputs.single().data
                "This must be an IOU transaction" using (output is IOUState)
            }
        }
        subFlow(signedTransactionFlow)
    }
}