/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.integrationtests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.apache.fineract.client.models.AdvancedPaymentData;
import org.apache.fineract.client.models.AllowAttributeOverrides;
import org.apache.fineract.client.models.ChargeData;
import org.apache.fineract.client.models.ChargeToGLAccountMapper;
import org.apache.fineract.client.models.GetJournalEntriesTransactionIdResponse;
import org.apache.fineract.client.models.GetLoanFeeToIncomeAccountMappings;
import org.apache.fineract.client.models.GetLoanPaymentChannelToFundSourceMappings;
import org.apache.fineract.client.models.GetLoanTransactionRelation;
import org.apache.fineract.client.models.GetLoansLoanIdResponse;
import org.apache.fineract.client.models.GetLoansLoanIdTransactionsTransactionIdResponse;
import org.apache.fineract.client.models.JournalEntryTransactionItem;
import org.apache.fineract.client.models.PostLoanProductsRequest;
import org.apache.fineract.client.models.PostLoanProductsResponse;
import org.apache.fineract.client.models.PostLoansLoanIdTransactionsRequest;
import org.apache.fineract.client.models.PostLoansLoanIdTransactionsResponse;
import org.apache.fineract.client.models.PostPaymentTypesRequest;
import org.apache.fineract.client.models.PostPaymentTypesResponse;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.PaymentTypeHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.accounting.Account;
import org.apache.fineract.integrationtests.common.charges.ChargesHelper;
import org.apache.fineract.integrationtests.common.funds.FundsHelper;
import org.apache.fineract.integrationtests.common.funds.FundsResourceHandler;
import org.apache.fineract.integrationtests.common.loans.LoanApplicationTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanTestLifecycleExtension;
import org.apache.fineract.integrationtests.common.loans.LoanTransactionHelper;
import org.apache.fineract.integrationtests.common.products.DelinquencyBucketsHelper;
import org.apache.fineract.integrationtests.common.system.CodeHelper;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleProcessingType;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(LoanTestLifecycleExtension.class)
public class LoanAccountChargeOffWithAdvancedPaymentAllocationTest extends BaseLoanIntegrationTest {

    // Charge-off accounting and balances
    @Test
    public void loanChargeOffWithAdvancedPaymentStrategyTest() {
        String loanExternalIdStr = UUID.randomUUID().toString();
        final Integer loanProductID = createLoanProductWithPeriodicAccrualAccountingAndAdvancedPaymentAllocationStrategy();
        final Integer clientId = CLIENT_HELPER.createClient(ClientHelper.defaultClientCreationRequest()).getClientId().intValue();
        final Integer loanId = createLoanAccount(clientId, loanProductID, loanExternalIdStr);

        // apply charges
        Integer feeCharge = ChargesHelper.createCharges(REQUEST_SPEC, RESPONSE_SPEC,
                ChargesHelper.getLoanSpecifiedDueDateJSON(ChargesHelper.CHARGE_CALCULATION_TYPE_FLAT, "10", false));

        LocalDate targetDate = LocalDate.of(2022, 9, 5);
        final String feeCharge1AddedDate = DATE_FORMATTER.format(targetDate);
        Integer feeLoanChargeId = LOAN_TRANSACTION_HELPER.addChargesForLoan(loanId,
                LoanTransactionHelper.getSpecifiedDueDateChargesForLoanAsJSON(String.valueOf(feeCharge), feeCharge1AddedDate, "10"));

        // apply penalty
        Integer penalty = ChargesHelper.createCharges(REQUEST_SPEC, RESPONSE_SPEC,
                ChargesHelper.getLoanSpecifiedDueDateJSON(ChargesHelper.CHARGE_CALCULATION_TYPE_FLAT, "10", true));

        final String penaltyCharge1AddedDate = DATE_FORMATTER.format(targetDate);

        Integer penalty1LoanChargeId = LOAN_TRANSACTION_HELPER.addChargesForLoan(loanId,
                LoanTransactionHelper.getSpecifiedDueDateChargesForLoanAsJSON(String.valueOf(penalty), penaltyCharge1AddedDate, "10"));

        // make Repayment
        final PostLoansLoanIdTransactionsResponse repaymentTransaction = LOAN_TRANSACTION_HELPER.makeLoanRepayment(loanExternalIdStr,
                new PostLoansLoanIdTransactionsRequest().dateFormat("dd MMMM yyyy").transactionDate("9 September 2022").locale("en")
                        .transactionAmount(10.0));

        GetLoansLoanIdResponse loanDetails = LOAN_TRANSACTION_HELPER.getLoanDetails((long) loanId);
        assertTrue(loanDetails.getStatus().getActive());

        // set loan as chargeoff
        String randomText = Utils.randomStringGenerator("en", 5) + Utils.randomNumberGenerator(6) + Utils.randomStringGenerator("is", 5);
        Integer chargeOffReasonId = CodeHelper.createChargeOffCodeValue(REQUEST_SPEC, RESPONSE_SPEC, randomText, 1);
        String transactionExternalId = UUID.randomUUID().toString();
        PostLoansLoanIdTransactionsResponse chargeOffTransaction = LOAN_TRANSACTION_HELPER.chargeOffLoan((long) loanId,
                new PostLoansLoanIdTransactionsRequest().transactionDate("10 September 2022").locale("en").dateFormat("dd MMMM yyyy")
                        .externalId(transactionExternalId).chargeOffReasonId((long) chargeOffReasonId));

        loanDetails = LOAN_TRANSACTION_HELPER.getLoanDetails((long) loanId);
        assertTrue(loanDetails.getStatus().getActive());
        assertTrue(loanDetails.getChargedOff());

        // verify amounts for charge-off transaction
        verifyTransaction(LocalDate.of(2022, 9, 10), 1010.0f, 1000.0f, 0.0f, 10.0f, 0.0f, loanId, "chargeoff");
        // verify journal entries
        GetJournalEntriesTransactionIdResponse journalEntriesForChargeOff = JOURNAL_ENTRY_HELPER
                .getJournalEntries("L" + chargeOffTransaction.getResourceId().toString());

        assertNotNull(journalEntriesForChargeOff);

        List<JournalEntryTransactionItem> journalEntries = journalEntriesForChargeOff.getPageItems();
        assertEquals(4, journalEntries.size());
        verifyJournalEntry(journalEntries.get(3), 1000.0, LocalDate.of(2022, 9, 10), LOANS_RECEIVABLE_ACCOUNT, "CREDIT");
        verifyJournalEntry(journalEntries.get(2), 10.0, LocalDate.of(2022, 9, 10), FEE_INCOME_ACCOUNT, "CREDIT");
        verifyJournalEntry(journalEntries.get(1), 1000.0, LocalDate.of(2022, 9, 10), CHARGE_OFF_EXPENSE_ACCOUNT, "DEBIT");
        verifyJournalEntry(journalEntries.get(0), 10.0, LocalDate.of(2022, 9, 10), FEE_CHARGE_OFF_ACCOUNT, "DEBIT");

    }

    // Reverse Replay of Charge-Off
    @Test
    public void loanChargeOffReverseReplayWithAdvancedPaymentStrategyTest() {
        runAt("9 September 2022", () -> {
            String loanExternalIdStr = UUID.randomUUID().toString();
            final Integer loanProductID = createLoanProductWithPeriodicAccrualAccountingAndAdvancedPaymentAllocationStrategy();
            final Integer clientId = CLIENT_HELPER.createClient(ClientHelper.defaultClientCreationRequest()).getClientId().intValue();
            final Integer loanId = createLoanAccount(clientId, loanProductID, loanExternalIdStr);

            // apply charges
            Integer feeCharge = ChargesHelper.createCharges(REQUEST_SPEC, RESPONSE_SPEC,
                    ChargesHelper.getLoanSpecifiedDueDateJSON(ChargesHelper.CHARGE_CALCULATION_TYPE_FLAT, "10", false));

            LocalDate targetDate = LocalDate.of(2022, 9, 5);
            final String feeCharge1AddedDate = DATE_FORMATTER.format(targetDate);
            Integer feeLoanChargeId = LOAN_TRANSACTION_HELPER.addChargesForLoan(loanId,
                    LoanTransactionHelper.getSpecifiedDueDateChargesForLoanAsJSON(String.valueOf(feeCharge), feeCharge1AddedDate, "10"));

            // apply penalty
            Integer penalty = ChargesHelper.createCharges(REQUEST_SPEC, RESPONSE_SPEC,
                    ChargesHelper.getLoanSpecifiedDueDateJSON(ChargesHelper.CHARGE_CALCULATION_TYPE_FLAT, "10", true));

            final String penaltyCharge1AddedDate = DATE_FORMATTER.format(targetDate);

            Integer penalty1LoanChargeId = LOAN_TRANSACTION_HELPER.addChargesForLoan(loanId,
                    LoanTransactionHelper.getSpecifiedDueDateChargesForLoanAsJSON(String.valueOf(penalty), penaltyCharge1AddedDate, "10"));

            // make Repayment
            final PostLoansLoanIdTransactionsResponse repaymentTransaction = LOAN_TRANSACTION_HELPER.makeLoanRepayment(loanExternalIdStr,
                    new PostLoansLoanIdTransactionsRequest().dateFormat("dd MMMM yyyy").transactionDate("9 September 2022").locale("en")
                            .transactionAmount(10.0));

            GetLoansLoanIdResponse loanDetails = LOAN_TRANSACTION_HELPER.getLoanDetails((long) loanId);
            assertTrue(loanDetails.getStatus().getActive());

            // set loan as chargeoff
            updateBusinessDate("10 September 2022");
            String randomText = Utils.randomStringGenerator("en", 5) + Utils.randomNumberGenerator(6)
                    + Utils.randomStringGenerator("is", 5);
            Integer chargeOffReasonId = CodeHelper.createChargeOffCodeValue(REQUEST_SPEC, RESPONSE_SPEC, randomText, 1);
            String transactionExternalId = UUID.randomUUID().toString();
            PostLoansLoanIdTransactionsResponse chargeOffTransaction = LOAN_TRANSACTION_HELPER.chargeOffLoan((long) loanId,
                    new PostLoansLoanIdTransactionsRequest().transactionDate("10 September 2022").locale("en").dateFormat("dd MMMM yyyy")
                            .externalId(transactionExternalId).chargeOffReasonId((long) chargeOffReasonId));

            loanDetails = LOAN_TRANSACTION_HELPER.getLoanDetails((long) loanId);
            assertTrue(loanDetails.getStatus().getActive());
            assertTrue(loanDetails.getChargedOff());

            // verify amounts for charge-off transaction
            verifyTransaction(LocalDate.of(2022, 9, 10), 1010.0f, 1000.0f, 0.0f, 10.0f, 0.0f, loanId, "chargeoff");

            Long reversedAndReplayedTransactionId = chargeOffTransaction.getResourceId();

            // reverse Repayment
            updateBusinessDate("11 September 2022");
            LOAN_TRANSACTION_HELPER.reverseRepayment(loanId, repaymentTransaction.getResourceId().intValue(), "11 September 2022");

            // verify chargeOffTransaction gets reverse replayed

            GetLoansLoanIdTransactionsTransactionIdResponse getLoansTransactionResponse = LOAN_TRANSACTION_HELPER
                    .getLoanTransactionDetails((long) loanId, transactionExternalId);
            assertNotNull(getLoansTransactionResponse);
            assertNotNull(getLoansTransactionResponse.getTransactionRelations());

            // test replayed relationship
            GetLoanTransactionRelation transactionRelation = getLoansTransactionResponse.getTransactionRelations().iterator().next();
            assertEquals(reversedAndReplayedTransactionId, transactionRelation.getToLoanTransaction());
            assertEquals("REPLAYED", transactionRelation.getRelationType());

            // verify amounts for charge-off transaction
            verifyTransaction(LocalDate.of(2022, 9, 10), 1020.0f, 1000.0f, 0.0f, 10.0f, 10.0f, loanId, "chargeoff");
        });
    }

    // undo Charge-Off
    @Test
    public void loanUndoChargeOffTest() {
        // Loan ExternalId
        String loanExternalIdStr = UUID.randomUUID().toString();

        final Integer loanProductID = createLoanProductWithPeriodicAccrualAccountingAndAdvancedPaymentAllocationStrategy();
        final Integer clientId = CLIENT_HELPER.createClient(ClientHelper.defaultClientCreationRequest()).getClientId().intValue();
        final Integer loanId = createLoanAccount(clientId, loanProductID, loanExternalIdStr);

        // make Repayment
        final PostLoansLoanIdTransactionsResponse repaymentTransaction = LOAN_TRANSACTION_HELPER.makeLoanRepayment(loanExternalIdStr,
                new PostLoansLoanIdTransactionsRequest().dateFormat("dd MMMM yyyy").transactionDate("6 September 2022").locale("en")
                        .transactionAmount(100.0));

        GetLoansLoanIdResponse loanDetails = LOAN_TRANSACTION_HELPER.getLoanDetails((long) loanId);
        assertTrue(loanDetails.getStatus().getActive());

        // set loan as chargeoff
        String randomText = Utils.randomStringGenerator("en", 5) + Utils.randomNumberGenerator(6) + Utils.randomStringGenerator("is", 5);
        Integer chargeOffReasonId = CodeHelper.createChargeOffCodeValue(REQUEST_SPEC, RESPONSE_SPEC, randomText, 1);
        String transactionExternalId = UUID.randomUUID().toString();
        LOAN_TRANSACTION_HELPER.chargeOffLoan((long) loanId, new PostLoansLoanIdTransactionsRequest().transactionDate("7 September 2022")
                .locale("en").dateFormat("dd MMMM yyyy").externalId(transactionExternalId).chargeOffReasonId((long) chargeOffReasonId));

        loanDetails = LOAN_TRANSACTION_HELPER.getLoanDetails((long) loanId);
        assertTrue(loanDetails.getStatus().getActive());
        assertTrue(loanDetails.getChargedOff());

        // undo charge-off
        String reverseTransactionExternalId = UUID.randomUUID().toString();
        PostLoansLoanIdTransactionsResponse undoChargeOffTxResponse = LOAN_TRANSACTION_HELPER.undoChargeOffLoan((long) loanId,
                new PostLoansLoanIdTransactionsRequest().reversalExternalId(reverseTransactionExternalId));
        assertNotNull(undoChargeOffTxResponse);

        loanDetails = LOAN_TRANSACTION_HELPER.getLoanDetails((long) loanId);
        assertTrue(loanDetails.getStatus().getActive());
        assertFalse(loanDetails.getChargedOff());

        GetLoansLoanIdTransactionsTransactionIdResponse chargeOffTransactionDetails = LOAN_TRANSACTION_HELPER
                .getLoanTransactionDetails((long) loanId, transactionExternalId);
        assertNotNull(chargeOffTransactionDetails);
        assertTrue(chargeOffTransactionDetails.getManuallyReversed());
        assertEquals(reverseTransactionExternalId, chargeOffTransactionDetails.getReversalExternalId());
    }

    // Backdated repayment transaction, Reverse replay of charge off
    @Test
    public void postChargeOffAddBackdatedTransactionAndReverseReplayTest() {
        runAt("3 September 2022", () -> {
            String loanExternalIdStr = UUID.randomUUID().toString();
            final Integer loanProductID = createLoanProductWithPeriodicAccrualAccountingAndAdvancedPaymentAllocationStrategy();
            final Integer clientId = CLIENT_HELPER.createClient(ClientHelper.defaultClientCreationRequest()).getClientId().intValue();
            final Integer loanId = createLoanAccount(clientId, loanProductID, loanExternalIdStr);

            // apply charges
            updateBusinessDate("5 September 2022");
            Integer feeCharge = ChargesHelper.createCharges(REQUEST_SPEC, RESPONSE_SPEC,
                    ChargesHelper.getLoanSpecifiedDueDateJSON(ChargesHelper.CHARGE_CALCULATION_TYPE_FLAT, "10", false));

            LocalDate targetDate = LocalDate.of(2022, 9, 5);
            final String feeCharge1AddedDate = DATE_FORMATTER.format(targetDate);
            Integer feeLoanChargeId = LOAN_TRANSACTION_HELPER.addChargesForLoan(loanId,
                    LoanTransactionHelper.getSpecifiedDueDateChargesForLoanAsJSON(String.valueOf(feeCharge), feeCharge1AddedDate, "10"));

            // set loan as chargeoff
            updateBusinessDate("14 September 2022");
            String randomText = Utils.randomStringGenerator("en", 5) + Utils.randomNumberGenerator(6)
                    + Utils.randomStringGenerator("is", 5);
            Integer chargeOffReasonId = CodeHelper.createChargeOffCodeValue(REQUEST_SPEC, RESPONSE_SPEC, randomText, 1);
            String transactionExternalId = UUID.randomUUID().toString();
            PostLoansLoanIdTransactionsResponse chargeOffTransaction = LOAN_TRANSACTION_HELPER.chargeOffLoan((long) loanId,
                    new PostLoansLoanIdTransactionsRequest().transactionDate("14 September 2022").locale("en").dateFormat("dd MMMM yyyy")
                            .externalId(transactionExternalId).chargeOffReasonId((long) chargeOffReasonId));

            GetLoansLoanIdResponse loanDetails = LOAN_TRANSACTION_HELPER.getLoanDetails((long) loanId);
            assertTrue(loanDetails.getStatus().getActive());
            assertTrue(loanDetails.getChargedOff());

            Long reversedAndReplayedTransactionId = chargeOffTransaction.getResourceId();

            // verify Journal Entries For ChargeOff Transaction
            GetJournalEntriesTransactionIdResponse journalEntriesForChargeOff = JOURNAL_ENTRY_HELPER
                    .getJournalEntries("L" + chargeOffTransaction.getResourceId().toString());

            assertNotNull(journalEntriesForChargeOff);
            List<JournalEntryTransactionItem> journalEntries = journalEntriesForChargeOff.getPageItems();
            assertEquals(4, journalEntries.size());

            verifyJournalEntry(journalEntries.get(3), 1000.0, LocalDate.of(2022, 9, 14), LOANS_RECEIVABLE_ACCOUNT, "CREDIT");
            verifyJournalEntry(journalEntries.get(2), 10.0, LocalDate.of(2022, 9, 14), FEE_INCOME_ACCOUNT, "CREDIT");
            verifyJournalEntry(journalEntries.get(1), 1000.0, LocalDate.of(2022, 9, 14), CHARGE_OFF_EXPENSE_ACCOUNT, "DEBIT");
            verifyJournalEntry(journalEntries.get(0), 10.0, LocalDate.of(2022, 9, 14), FEE_CHARGE_OFF_ACCOUNT, "DEBIT");

            // make Repayment before chargeoff date - business date is still on 14 September 2022
            final PostLoansLoanIdTransactionsResponse repaymentTransaction = LOAN_TRANSACTION_HELPER.makeLoanRepayment(loanExternalIdStr,
                    new PostLoansLoanIdTransactionsRequest().dateFormat("dd MMMM yyyy").transactionDate("7 September 2022").locale("en")
                            .transactionAmount(100.0));

            loanDetails = LOAN_TRANSACTION_HELPER.getLoanDetails((long) loanId);
            assertTrue(loanDetails.getStatus().getActive());
            assertTrue(loanDetails.getChargedOff());

            // verify Journal Entries for Repayment transaction

            GetJournalEntriesTransactionIdResponse journalEntriesForRepayment = JOURNAL_ENTRY_HELPER
                    .getJournalEntries("L" + repaymentTransaction.getResourceId().toString());
            assertNotNull(journalEntriesForRepayment);

            journalEntries = journalEntriesForRepayment.getPageItems();
            assertEquals(3, journalEntries.size());

            verifyJournalEntry(journalEntries.get(2), 90.0, LocalDate.of(2022, 9, 7), LOANS_RECEIVABLE_ACCOUNT, "CREDIT");
            verifyJournalEntry(journalEntries.get(1), 10.0, LocalDate.of(2022, 9, 7), FEE_INCOME_ACCOUNT, "CREDIT");
            verifyJournalEntry(journalEntries.get(0), 100.0, LocalDate.of(2022, 9, 7), SUSPENSE_ACCOUNT, "DEBIT");

            // verify reverse replay of Charge-Off

            GetLoansLoanIdTransactionsTransactionIdResponse getLoansTransactionResponse = LOAN_TRANSACTION_HELPER
                    .getLoanTransactionDetails((long) loanId, transactionExternalId);
            assertNotNull(getLoansTransactionResponse);
            assertNotNull(getLoansTransactionResponse.getTransactionRelations());

            // test replayed relationship
            GetLoanTransactionRelation transactionRelation = getLoansTransactionResponse.getTransactionRelations().iterator().next();
            assertEquals(reversedAndReplayedTransactionId, transactionRelation.getToLoanTransaction());
            assertEquals("REPLAYED", transactionRelation.getRelationType());

            // verify amounts for charge-off transaction
            verifyTransaction(LocalDate.of(2022, 9, 14), 910.0f, 910.0f, 0.0f, 0.0f, 0.0f, loanId, "chargeoff");

            // make Repayment after chargeoff date
            updateBusinessDate("15 September 2022");
            final PostLoansLoanIdTransactionsResponse repaymentTransaction_1 = LOAN_TRANSACTION_HELPER.makeLoanRepayment(loanExternalIdStr,
                    new PostLoansLoanIdTransactionsRequest().dateFormat("dd MMMM yyyy").transactionDate("15 September 2022").locale("en")
                            .transactionAmount(100.0));

            loanDetails = LOAN_TRANSACTION_HELPER.getLoanDetails((long) loanId);
            assertTrue(loanDetails.getStatus().getActive());
            assertTrue(loanDetails.getChargedOff());

            // verify Journal Entries for Repayment transaction
            journalEntriesForRepayment = JOURNAL_ENTRY_HELPER.getJournalEntries("L" + repaymentTransaction_1.getResourceId().toString());

            assertNotNull(journalEntriesForRepayment);

            journalEntries = journalEntriesForRepayment.getPageItems();
            assertEquals(2, journalEntries.size());

            verifyJournalEntry(journalEntries.get(1), 100.0, LocalDate.of(2022, 9, 15), RECOVERIES_ACCOUNT, "CREDIT");
            verifyJournalEntry(journalEntries.get(0), 100.0, LocalDate.of(2022, 9, 15), SUSPENSE_ACCOUNT, "DEBIT");
        });
    }

    // Repayment before charge off on charge off date, reverse replay of charge off
    @Test
    public void transactionOnChargeOffDateReverseTest() {
        runAt("7 September 2022", () -> {
            String loanExternalIdStr = UUID.randomUUID().toString();
            final Integer loanProductID = createLoanProductWithPeriodicAccrualAccountingAndAdvancedPaymentAllocationStrategy();
            final Integer clientId = CLIENT_HELPER.createClient(ClientHelper.defaultClientCreationRequest()).getClientId().intValue();
            final Integer loanId = createLoanAccount(clientId, loanProductID, loanExternalIdStr);

            // apply charges
            Integer feeCharge = ChargesHelper.createCharges(REQUEST_SPEC, RESPONSE_SPEC,
                    ChargesHelper.getLoanSpecifiedDueDateJSON(ChargesHelper.CHARGE_CALCULATION_TYPE_FLAT, "10", false));

            LocalDate targetDate = LocalDate.of(2022, 9, 5);
            final String feeCharge1AddedDate = DATE_FORMATTER.format(targetDate);
            Integer feeLoanChargeId = LOAN_TRANSACTION_HELPER.addChargesForLoan(loanId,
                    LoanTransactionHelper.getSpecifiedDueDateChargesForLoanAsJSON(String.valueOf(feeCharge), feeCharge1AddedDate, "10"));

            // make Repayment before charge-off on charge off date
            final PostLoansLoanIdTransactionsResponse repaymentTransaction = LOAN_TRANSACTION_HELPER.makeLoanRepayment(loanExternalIdStr,
                    new PostLoansLoanIdTransactionsRequest().dateFormat("dd MMMM yyyy").transactionDate("7 September 2022").locale("en")
                            .transactionAmount(100.0));

            GetLoansLoanIdResponse loanDetails = LOAN_TRANSACTION_HELPER.getLoanDetails((long) loanId);
            assertTrue(loanDetails.getStatus().getActive());

            // verify Journal Entries for Repayment transaction
            GetJournalEntriesTransactionIdResponse journalEntriesForRepayment = JOURNAL_ENTRY_HELPER
                    .getJournalEntries("L" + repaymentTransaction.getResourceId().toString());

            assertNotNull(journalEntriesForRepayment);

            List<JournalEntryTransactionItem> journalEntries = journalEntriesForRepayment.getPageItems();
            assertEquals(3, journalEntries.size());

            verifyJournalEntry(journalEntries.get(2), 90.0, LocalDate.of(2022, 9, 7), LOANS_RECEIVABLE_ACCOUNT, "CREDIT");
            verifyJournalEntry(journalEntries.get(1), 10.0, LocalDate.of(2022, 9, 7), FEE_INCOME_ACCOUNT, "CREDIT");
            verifyJournalEntry(journalEntries.get(0), 100.0, LocalDate.of(2022, 9, 7), SUSPENSE_ACCOUNT, "DEBIT");

            // set loan as chargeoff
            String randomText = Utils.randomStringGenerator("en", 5) + Utils.randomNumberGenerator(6)
                    + Utils.randomStringGenerator("is", 5);
            Integer chargeOffReasonId = CodeHelper.createChargeOffCodeValue(REQUEST_SPEC, RESPONSE_SPEC, randomText, 1);
            String transactionExternalId = UUID.randomUUID().toString();
            PostLoansLoanIdTransactionsResponse chargeOffTransaction = LOAN_TRANSACTION_HELPER.chargeOffLoan((long) loanId,
                    new PostLoansLoanIdTransactionsRequest().transactionDate("7 September 2022").locale("en").dateFormat("dd MMMM yyyy")
                            .externalId(transactionExternalId).chargeOffReasonId((long) chargeOffReasonId));

            loanDetails = LOAN_TRANSACTION_HELPER.getLoanDetails((long) loanId);
            assertTrue(loanDetails.getStatus().getActive());
            assertTrue(loanDetails.getChargedOff());

            Long reversedAndReplayedTransactionId = chargeOffTransaction.getResourceId();

            // verify Journal Entries For ChargeOff Transaction
            GetJournalEntriesTransactionIdResponse journalEntriesForChargeOff = JOURNAL_ENTRY_HELPER
                    .getJournalEntries("L" + chargeOffTransaction.getResourceId().toString());

            assertNotNull(journalEntriesForChargeOff);
            journalEntries = journalEntriesForChargeOff.getPageItems();
            assertEquals(2, journalEntries.size());

            verifyJournalEntry(journalEntries.get(1), 910.0, LocalDate.of(2022, 9, 7), LOANS_RECEIVABLE_ACCOUNT, "CREDIT");
            verifyJournalEntry(journalEntries.get(0), 910.0, LocalDate.of(2022, 9, 7), CHARGE_OFF_EXPENSE_ACCOUNT, "DEBIT");

            // reverse Repayment
            LOAN_TRANSACTION_HELPER.reverseRepayment(loanId, repaymentTransaction.getResourceId().intValue(), "7 September 2022");
            loanDetails = LOAN_TRANSACTION_HELPER.getLoanDetails((long) loanId);
            assertTrue(loanDetails.getStatus().getActive());
            assertTrue(loanDetails.getChargedOff());

            // verify Journal Entries for Reversed Repayment transaction
            journalEntriesForRepayment = JOURNAL_ENTRY_HELPER.getJournalEntries("L" + repaymentTransaction.getResourceId().toString());
            assertNotNull(journalEntriesForRepayment);

            journalEntries = journalEntriesForRepayment.getPageItems();
            assertEquals(6, journalEntries.size());

            verifyJournalEntry(journalEntries.get(5), 90.0, LocalDate.of(2022, 9, 7), LOANS_RECEIVABLE_ACCOUNT, "CREDIT");
            verifyJournalEntry(journalEntries.get(4), 10.0, LocalDate.of(2022, 9, 7), FEE_INCOME_ACCOUNT, "CREDIT");
            verifyJournalEntry(journalEntries.get(3), 100.0, LocalDate.of(2022, 9, 7), SUSPENSE_ACCOUNT, "DEBIT");
            verifyJournalEntry(journalEntries.get(2), 90.0, LocalDate.of(2022, 9, 7), LOANS_RECEIVABLE_ACCOUNT, "DEBIT");
            verifyJournalEntry(journalEntries.get(1), 10.0, LocalDate.of(2022, 9, 7), FEE_INCOME_ACCOUNT, "DEBIT");
            verifyJournalEntry(journalEntries.get(0), 100.0, LocalDate.of(2022, 9, 7), SUSPENSE_ACCOUNT, "CREDIT");

            // verify reverse replay of Charge-Off

            GetLoansLoanIdTransactionsTransactionIdResponse getLoansTransactionResponse = LOAN_TRANSACTION_HELPER
                    .getLoanTransactionDetails((long) loanId, transactionExternalId);
            assertNotNull(getLoansTransactionResponse);
            assertNotNull(getLoansTransactionResponse.getTransactionRelations());

            // test replayed relationship
            GetLoanTransactionRelation transactionRelation = getLoansTransactionResponse.getTransactionRelations().iterator().next();
            assertEquals(reversedAndReplayedTransactionId, transactionRelation.getToLoanTransaction());
            assertEquals("REPLAYED", transactionRelation.getRelationType());

            // verify amounts for charge-off transaction
            verifyTransaction(LocalDate.of(2022, 9, 7), 1010.0f, 1000.0f, 0.0f, 10.0f, 0.0f, loanId, "chargeoff");
        });

    }

    private void verifyJournalEntry(JournalEntryTransactionItem journalEntryTransactionItem, Double amount, LocalDate entryDate,
            Account account, String type) {
        assertEquals(amount, journalEntryTransactionItem.getAmount());
        assertEquals(entryDate, journalEntryTransactionItem.getTransactionDate());
        assertEquals(account.getAccountID().longValue(), journalEntryTransactionItem.getGlAccountId().longValue());
        assertEquals(type, journalEntryTransactionItem.getEntryType().getValue());
    }

    private void verifyTransaction(final LocalDate transactionDate, final Float transactionAmount, final Float principalPortion,
            final Float interestPortion, final Float feePortion, final Float penaltyPortion, final Integer loanID,
            final String transactionOfType) {
        ArrayList<HashMap> transactions = (ArrayList<HashMap>) LOAN_TRANSACTION_HELPER.getLoanTransactions(REQUEST_SPEC, RESPONSE_SPEC,
                loanID);
        boolean isTransactionFound = false;
        for (int i = 0; i < transactions.size(); i++) {
            HashMap transactionType = (HashMap) transactions.get(i).get("type");
            boolean isTransaction = (Boolean) transactionType.get(transactionOfType);

            if (isTransaction) {
                ArrayList<Integer> transactionDateAsArray = (ArrayList<Integer>) transactions.get(i).get("date");
                LocalDate transactionEntryDate = LocalDate.of(transactionDateAsArray.get(0), transactionDateAsArray.get(1),
                        transactionDateAsArray.get(2));

                if (transactionDate.isEqual(transactionEntryDate)) {
                    isTransactionFound = true;
                    assertEquals(transactionAmount, Float.valueOf(String.valueOf(transactions.get(i).get("amount"))),
                            "Mismatch in transaction amounts");
                    assertEquals(principalPortion, Float.valueOf(String.valueOf(transactions.get(i).get("principalPortion"))),
                            "Mismatch in transaction amounts");
                    assertEquals(interestPortion, Float.valueOf(String.valueOf(transactions.get(i).get("interestPortion"))),
                            "Mismatch in transaction amounts");
                    assertEquals(feePortion, Float.valueOf(String.valueOf(transactions.get(i).get("feeChargesPortion"))),
                            "Mismatch in transaction amounts");
                    assertEquals(penaltyPortion, Float.valueOf(String.valueOf(transactions.get(i).get("penaltyChargesPortion"))),
                            "Mismatch in transaction amounts");
                    break;
                }
            }
        }
        assertTrue(isTransactionFound, "No Transaction entries are posted");
    }

    private Integer createLoanAccount(final Integer clientID, final Integer loanProductID, final String externalId) {

        String loanApplicationJSON = new LoanApplicationTestBuilder().withPrincipal("1000").withLoanTermFrequency("30")
                .withLoanTermFrequencyAsDays().withNumberOfRepayments("1").withRepaymentEveryAfter("30").withRepaymentFrequencyTypeAsDays()
                .withInterestRatePerPeriod("0").withInterestTypeAsFlatBalance().withAmortizationTypeAsEqualPrincipalPayments()
                .withInterestCalculationPeriodTypeSameAsRepaymentPeriod().withExpectedDisbursementDate("03 September 2022")
                .withSubmittedOnDate("01 September 2022").withLoanType("individual").withExternalId(externalId)
                .withRepaymentStrategy("advanced-payment-allocation-strategy").build(clientID.toString(), loanProductID.toString(), null);

        final Integer loanId = LOAN_TRANSACTION_HELPER.getLoanId(loanApplicationJSON);
        LOAN_TRANSACTION_HELPER.approveLoan("02 September 2022", "1000", loanId, null);
        LOAN_TRANSACTION_HELPER.disburseLoanWithTransactionAmount("03 September 2022", loanId, "1000");
        return loanId;
    }

    private Integer createLoanProductWithPeriodicAccrualAccountingAndAdvancedPaymentAllocationStrategy() {

        String name = Utils.uniqueRandomStringGenerator("LOAN_PRODUCT_", 6);
        String shortName = Utils.uniqueRandomStringGenerator("", 4);

        List<Integer> principalVariationsForBorrowerCycle = new ArrayList<>();
        List<Integer> numberOfRepaymentVariationsForBorrowerCycle = new ArrayList<>();
        List<Integer> interestRateVariationsForBorrowerCycle = new ArrayList<>();
        List<ChargeData> charges = new ArrayList<>();
        List<ChargeToGLAccountMapper> penaltyToIncomeAccountMappings = new ArrayList<>();
        List<GetLoanFeeToIncomeAccountMappings> feeToIncomeAccountMappings = new ArrayList<>();

        String paymentTypeName = PaymentTypeHelper.randomNameGenerator("P_T", 5);
        String description = PaymentTypeHelper.randomNameGenerator("PT_Desc", 15);
        Boolean isCashPayment = false;
        Integer position = 1;

        PostPaymentTypesResponse paymentTypesResponse = PAYMENT_TYPE_HELPER.createPaymentType(new PostPaymentTypesRequest()
                .name(paymentTypeName).description(description).isCashPayment(isCashPayment).position(position));
        Long paymentTypeIdOne = paymentTypesResponse.getResourceId();
        Assertions.assertNotNull(paymentTypeIdOne);

        List<GetLoanPaymentChannelToFundSourceMappings> paymentChannelToFundSourceMappings = new ArrayList<>();
        GetLoanPaymentChannelToFundSourceMappings loanPaymentChannelToFundSourceMappings = new GetLoanPaymentChannelToFundSourceMappings();
        loanPaymentChannelToFundSourceMappings.fundSourceAccountId(FUND_SOURCE.getAccountID().longValue());
        loanPaymentChannelToFundSourceMappings.paymentTypeId(paymentTypeIdOne.longValue());
        paymentChannelToFundSourceMappings.add(loanPaymentChannelToFundSourceMappings);

        // fund
        FundsHelper fh = FundsHelper.create(Utils.uniqueRandomStringGenerator("", 10)).externalId(UUID.randomUUID().toString()).build();
        String jsonData = fh.toJSON();

        final Long fundID = createFund(jsonData);
        Assertions.assertNotNull(fundID);

        // Delinquency Bucket
        final Integer delinquencyBucketId = DelinquencyBucketsHelper.createDelinquencyBucket(REQUEST_SPEC, RESPONSE_SPEC);

        String futureInstallmentAllocationRule = "NEXT_INSTALLMENT";
        AdvancedPaymentData defaultAllocation = createDefaultPaymentAllocation(futureInstallmentAllocationRule);

        PostLoanProductsRequest loanProductsRequest = new PostLoanProductsRequest().name(name)//
                .shortName(shortName)//
                .description("Loan Product Description")//
                .fundId(fundID)//
                .startDate(null)//
                .closeDate(null)//
                .includeInBorrowerCycle(false)//
                .currencyCode("USD")//
                .digitsAfterDecimal(2)//
                .inMultiplesOf(0)//
                .installmentAmountInMultiplesOf(1)//
                .useBorrowerCycle(false)//
                .minPrincipal(100.0)//
                .principal(1000.0)//
                .maxPrincipal(10000.0)//
                .minNumberOfRepayments(1)//
                .numberOfRepayments(1)//
                .maxNumberOfRepayments(30)//
                .isLinkedToFloatingInterestRates(false)//
                .minInterestRatePerPeriod((double) 0)//
                .interestRatePerPeriod((double) 0)//
                .maxInterestRatePerPeriod((double) 0)//
                .interestRateFrequencyType(2)//
                .repaymentEvery(30)//
                .repaymentFrequencyType(0L)//
                .principalVariationsForBorrowerCycle(principalVariationsForBorrowerCycle)//
                .numberOfRepaymentVariationsForBorrowerCycle(numberOfRepaymentVariationsForBorrowerCycle)//
                .interestRateVariationsForBorrowerCycle(interestRateVariationsForBorrowerCycle)//
                .amortizationType(1)//
                .interestType(0)//
                .isEqualAmortization(false)//
                .interestCalculationPeriodType(1)//
                .transactionProcessingStrategyCode("advanced-payment-allocation-strategy")//
                .loanScheduleType(LoanScheduleType.PROGRESSIVE.toString())//
                .loanScheduleProcessingType(LoanScheduleProcessingType.HORIZONTAL.toString())//
                .addPaymentAllocationItem(defaultAllocation)//
                .daysInYearType(1)//
                .daysInMonthType(1)//
                .canDefineInstallmentAmount(true)//
                .graceOnArrearsAgeing(3)//
                .overdueDaysForNPA(179)//
                .accountMovesOutOfNPAOnlyOnArrearsCompletion(false)//
                .principalThresholdForLastInstallment(50)//
                .allowVariableInstallments(false)//
                .canUseForTopup(false)//
                .isInterestRecalculationEnabled(false)//
                .holdGuaranteeFunds(false)//
                .multiDisburseLoan(true)//
                .allowAttributeOverrides(new AllowAttributeOverrides()//
                        .amortizationType(true)//
                        .interestType(true)//
                        .transactionProcessingStrategyCode(true)//
                        .interestCalculationPeriodType(true)//
                        .inArrearsTolerance(true)//
                        .repaymentEvery(true)//
                        .graceOnPrincipalAndInterestPayment(true)//
                        .graceOnArrearsAgeing(true))//
                .allowPartialPeriodInterestCalcualtion(true)//
                .maxTrancheCount(10)//
                .outstandingLoanBalance(10000.0)//
                .charges(charges)//
                .accountingRule(3)//
                .fundSourceAccountId(SUSPENSE_ACCOUNT.getAccountID().longValue())//
                .loanPortfolioAccountId(LOANS_RECEIVABLE_ACCOUNT.getAccountID().longValue())//
                .transfersInSuspenseAccountId(SUSPENSE_ACCOUNT.getAccountID().longValue())//
                .interestOnLoanAccountId(INTEREST_INCOME_ACCOUNT.getAccountID().longValue())//
                .incomeFromFeeAccountId(FEE_INCOME_ACCOUNT.getAccountID().longValue())//
                .incomeFromPenaltyAccountId(FEE_INCOME_ACCOUNT.getAccountID().longValue())//
                .incomeFromRecoveryAccountId(RECOVERIES_ACCOUNT.getAccountID().longValue())//
                .writeOffAccountId(WRITTEN_OFF_ACCOUNT.getAccountID().longValue())//
                .overpaymentLiabilityAccountId(OVERPAYMENT_ACCOUNT.getAccountID().longValue())//
                .receivableInterestAccountId(FEE_INCOME_ACCOUNT.getAccountID().longValue())//
                .receivableFeeAccountId(FEE_INCOME_ACCOUNT.getAccountID().longValue())//
                .receivablePenaltyAccountId(FEE_INCOME_ACCOUNT.getAccountID().longValue())//
                .dateFormat("dd MMMM yyyy")//
                .locale("en_GB")//
                .disallowExpectedDisbursements(true)//
                .allowApprovedDisbursedAmountsOverApplied(true)//
                .overAppliedCalculationType("percentage")//
                .overAppliedNumber(50)//
                .delinquencyBucketId(delinquencyBucketId.longValue())//
                .goodwillCreditAccountId(GOODWILL_EXPENSE_ACCOUNT.getAccountID().longValue())//
                .incomeFromGoodwillCreditInterestAccountId(INTEREST_INCOME_CHARGE_OFF_ACCOUNT.getAccountID().longValue())//
                .incomeFromGoodwillCreditFeesAccountId(FEE_CHARGE_OFF_ACCOUNT.getAccountID().longValue())//
                .incomeFromGoodwillCreditPenaltyAccountId(FEE_CHARGE_OFF_ACCOUNT.getAccountID().longValue())//
                .paymentChannelToFundSourceMappings(paymentChannelToFundSourceMappings)//
                .penaltyToIncomeAccountMappings(penaltyToIncomeAccountMappings)//
                .feeToIncomeAccountMappings(feeToIncomeAccountMappings)//
                .incomeFromChargeOffInterestAccountId(INTEREST_INCOME_CHARGE_OFF_ACCOUNT.getAccountID().longValue())//
                .incomeFromChargeOffFeesAccountId(FEE_CHARGE_OFF_ACCOUNT.getAccountID().longValue())//
                .chargeOffExpenseAccountId(CHARGE_OFF_EXPENSE_ACCOUNT.getAccountID().longValue())//
                .chargeOffFraudExpenseAccountId(CHARGE_OFF_FRAUD_EXPENSE_ACCOUNT.getAccountID().longValue())//
                .incomeFromChargeOffPenaltyAccountId(FEE_CHARGE_OFF_ACCOUNT.getAccountID().longValue());//

        PostLoanProductsResponse loanProductCreateResponse = LOAN_PRODUCT_HELPER.createLoanProduct(loanProductsRequest);
        return loanProductCreateResponse.getResourceId().intValue();
    }

    private Long createFund(final String fundJSON) {
        String fundId = String.valueOf(FundsResourceHandler.createFund(fundJSON, REQUEST_SPEC, RESPONSE_SPEC));
        if (fundId.equals("null")) {
            // Invalid JSON data parameters
            return null;
        }

        return Long.valueOf(fundId);
    }
}
