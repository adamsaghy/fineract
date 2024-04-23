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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.client.models.GetLoansLoanIdResponse;
import org.apache.fineract.client.models.PostLoanProductsRequest;
import org.apache.fineract.client.models.PostLoanProductsResponse;
import org.apache.fineract.client.models.PostLoansLoanIdRequest;
import org.apache.fineract.client.models.PostLoansLoanIdTransactionsRequest;
import org.apache.fineract.client.models.PostLoansLoanIdTransactionsResponse;
import org.apache.fineract.client.models.PostLoansLoanIdTransactionsTransactionIdRequest;
import org.apache.fineract.client.models.PostLoansRequest;
import org.apache.fineract.client.models.PostLoansResponse;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.CommonConstants;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.accounting.Account;
import org.apache.fineract.integrationtests.common.accounting.JournalEntry;
import org.apache.fineract.integrationtests.common.loans.LoanApplicationTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanProductTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanStatusChecker;
import org.apache.fineract.integrationtests.common.loans.LoanTestLifecycleExtension;
import org.apache.fineract.integrationtests.common.loans.LoanTransactionHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@SuppressWarnings({ "rawtypes", "unchecked" })
@ExtendWith(LoanTestLifecycleExtension.class)
@Slf4j
public class ClientLoanCreditBalanceRefundandRepaymentTypeIntegrationTest extends BaseLoanIntegrationTest {

    private static final String CASH_BASED = "2";
    private static final String ACCRUAL_PERIODIC = "3";
    private static final String REPAYMENT = "repayment";
    private static final String MERCHANT_ISSUED_REFUND = "merchantIssuedRefund";
    private static final String PAYOUT_REFUND = "payoutRefund";
    private static final String GOODWILL_CREDIT = "goodwillCredit";
    private LoanTransactionHelper loanTransactionHelperValidationError;
    private Integer disbursedLoanID;

    @BeforeEach
    public void setup() {
        Utils.initializeRESTAssured();
        loanTransactionHelperValidationError = new LoanTransactionHelper(REQUEST_SPEC, RESPONSE_SPEC_403);
    }

    private void disburseLoanOfAccountingRule(final String accountingType, LoanProductTestBuilder loanProductTestBuilder) {
        final String principal = "12000.00";
        final String submitApproveDisburseDate = "01 January 2022";
        disbursedLoanID = fromStartToDisburseLoan(loanProductTestBuilder, submitApproveDisburseDate, principal, accountingType,
                LOANS_RECEIVABLE_ACCOUNT, FEE_INCOME_ACCOUNT, CHARGE_OFF_EXPENSE_ACCOUNT, OVERPAYMENT_ACCOUNT);
    }

    private Integer createLoanProduct(LoanProductTestBuilder loanProductTestBuilder, final String principal,
            final boolean multiDisburseLoan, final String accountingRule, final Account... accounts) {
        log.info("------------------------------CREATING NEW LOAN PRODUCT ---------------------------------------");
        loanProductTestBuilder = loanProductTestBuilder //
                .withPrincipal(principal) //
                .withShortName(Utils.uniqueRandomStringGenerator("", 4)) //
                .withNumberOfRepayments("4") //
                .withRepaymentAfterEvery("1") //
                .withRepaymentTypeAsMonth() //
                .withinterestRatePerPeriod("1") //
                .withInterestRateFrequencyTypeAsMonths() //
                .withAmortizationTypeAsEqualInstallments() //
                .withInterestTypeAsDecliningBalance() //
                .withAccounting(accountingRule, accounts) //
                .withTranches(multiDisburseLoan);
        if (multiDisburseLoan) {
            loanProductTestBuilder = loanProductTestBuilder.withInterestCalculationPeriodTypeAsRepaymentPeriod(true);
            loanProductTestBuilder = loanProductTestBuilder.withMaxTrancheCount("30");
        }
        final String loanProductJSON = loanProductTestBuilder.build(null);
        return LOAN_TRANSACTION_HELPER.getLoanProductId(loanProductJSON);
    }

    private Integer applyForLoanApplication(final Integer clientID, final Integer loanProductID, String principal, String submitDate,
            String repaymentStrategy) {
        log.info("--------------------------------APPLYING FOR LOAN APPLICATION--------------------------------");
        final String loanApplicationJSON = new LoanApplicationTestBuilder() //
                .withPrincipal(principal) //
                .withLoanTermFrequency("4") //
                .withLoanTermFrequencyAsMonths() //
                .withNumberOfRepayments("4") //
                .withRepaymentEveryAfter("1") //
                .withRepaymentFrequencyTypeAsMonths() //
                .withInterestRatePerPeriod("2") //
                .withAmortizationTypeAsEqualInstallments() //
                .withInterestTypeAsDecliningBalance() //
                .withInterestCalculationPeriodTypeSameAsRepaymentPeriod() //
                .withExpectedDisbursementDate(submitDate) //
                .withSubmittedOnDate(submitDate) //
                .withRepaymentStrategy(repaymentStrategy) //
                .build(clientID.toString(), loanProductID.toString(), null);
        return LOAN_TRANSACTION_HELPER.getLoanId(loanApplicationJSON);
    }

    private Integer fromStartToDisburseLoan(LoanProductTestBuilder loanProductTestBuilder, String submitApproveDisburseDate,
            String principal, final String accountingRule, final Account... accounts) {

        final Integer clientID = ClientHelper.createClient(REQUEST_SPEC, RESPONSE_SPEC);
        ClientHelper.verifyClientCreatedOnServer(REQUEST_SPEC, RESPONSE_SPEC, clientID);

        boolean allowMultipleDisbursals = false;
        final Integer loanProductID = createLoanProduct(loanProductTestBuilder, principal, allowMultipleDisbursals, accountingRule,
                accounts);
        Assertions.assertNotNull(loanProductID);

        final Integer loanID = applyForLoanApplication(clientID, loanProductID, principal, submitApproveDisburseDate,
                loanProductTestBuilder.getTransactionProcessingStrategyCode());
        Assertions.assertNotNull(loanID);
        HashMap loanStatusHashMap = LoanStatusChecker.getStatusOfLoan(REQUEST_SPEC, RESPONSE_SPEC, loanID);
        LoanStatusChecker.verifyLoanIsPending(loanStatusHashMap);

        log.info("-----------------------------------APPROVE LOAN-----------------------------------------");
        loanStatusHashMap = LOAN_TRANSACTION_HELPER.approveLoan(submitApproveDisburseDate, loanID);
        LoanStatusChecker.verifyLoanIsApproved(loanStatusHashMap);
        LoanStatusChecker.verifyLoanIsWaitingForDisbursal(loanStatusHashMap);

        log.info("-------------------------------DISBURSE LOAN -------------------------------------------"); //
        // String loanDetails = LOAN_TRANSACTION_HELPER.getLoanDetails(REQUEST_SPEC, RESPONSE_SPEC, loanID);
        loanStatusHashMap = LOAN_TRANSACTION_HELPER.disburseLoanWithNetDisbursalAmount(submitApproveDisburseDate, loanID, principal);
        LoanStatusChecker.verifyLoanIsActive(loanStatusHashMap);
        return loanID;
    }

    private HashMap makeRepayment(final String repaymentDate, final Float repayment) {
        log.info("-------------Make repayment -----------");
        LOAN_TRANSACTION_HELPER.makeRepayment(repaymentDate, repayment, disbursedLoanID);
        HashMap loanStatusHashMap = (HashMap) LOAN_TRANSACTION_HELPER.getLoanDetail(REQUEST_SPEC, RESPONSE_SPEC, disbursedLoanID, "status");
        return loanStatusHashMap;
    }

    @ParameterizedTest
    @MethodSource("loanProductFactory")
    public void creditBalanceRefundCanOnlyBeAppliedWhereLoanStatusIsOverpaidTest(LoanProductTestBuilder loanProductTestBuilder) {
        disburseLoanOfAccountingRule(ACCRUAL_PERIODIC, loanProductTestBuilder);
        HashMap loanStatusHashMap = makeRepayment("06 January 2022", 2000.00f); // not full payment
        LoanStatusChecker.verifyLoanIsActive(loanStatusHashMap);

        final String creditBalanceRefundDate = "09 January 2022";
        final Float refund = 1000.00f;
        final String externalId = null;
        ArrayList<HashMap> cbrErrors = (ArrayList<HashMap>) loanTransactionHelperValidationError
                .creditBalanceRefund(creditBalanceRefundDate, refund, externalId, disbursedLoanID, CommonConstants.RESPONSE_ERROR);

        assertEquals("error.msg.loan.credit.balance.refund.account.is.not.overpaid",
                cbrErrors.get(0).get(CommonConstants.RESPONSE_ERROR_MESSAGE_CODE));

    }

    @ParameterizedTest
    @MethodSource("loanProductFactory")
    public void cantRefundMoreThanOverpaidTest(LoanProductTestBuilder loanProductTestBuilder) {
        disburseLoanOfAccountingRule(ACCRUAL_PERIODIC, loanProductTestBuilder);
        HashMap loanStatusHashMap = makeRepayment("06 January 2022", 20000.00f); // overpayment
        LoanStatusChecker.verifyLoanAccountIsOverPaid(loanStatusHashMap);

        final String creditBalanceRefundDate = "09 January 2022";
        Float refund = 10000.00f;
        final String externalId = null;
        ArrayList<HashMap> cbrErrors = (ArrayList<HashMap>) loanTransactionHelperValidationError
                .creditBalanceRefund(creditBalanceRefundDate, refund, externalId, disbursedLoanID, CommonConstants.RESPONSE_ERROR);

        assertEquals("error.msg.transactionAmount.invalid.must.be.>zero.and<=overpaidamount",
                cbrErrors.get(0).get(CommonConstants.RESPONSE_ERROR_MESSAGE_CODE));

        refund = (float) -1.00;
        cbrErrors = (ArrayList<HashMap>) loanTransactionHelperValidationError.creditBalanceRefund(creditBalanceRefundDate, refund,
                externalId, disbursedLoanID, CommonConstants.RESPONSE_ERROR);
        assertEquals("validation.msg.loan.transaction.transactionAmount.not.greater.than.zero",
                cbrErrors.get(0).get(CommonConstants.RESPONSE_ERROR_MESSAGE_CODE));

    }

    @ParameterizedTest
    @MethodSource("loanProductFactory")
    public void fullRefundChangesStatusToClosedObligationMetTest(LoanProductTestBuilder loanProductTestBuilder) {
        disburseLoanOfAccountingRule(ACCRUAL_PERIODIC, loanProductTestBuilder);
        HashMap loanStatusHashMap = makeRepayment("06 January 2022", 20000.00f); // overpayment
        LoanStatusChecker.verifyLoanAccountIsOverPaid(loanStatusHashMap);

        final Float totalOverpaid = (Float) LOAN_TRANSACTION_HELPER.getLoanDetail(REQUEST_SPEC, RESPONSE_SPEC, disbursedLoanID,
                "totalOverpaid");

        final String creditBalanceRefundDate = "09 January 2022";
        final String externalId = null;
        LOAN_TRANSACTION_HELPER.creditBalanceRefund(creditBalanceRefundDate, totalOverpaid, externalId, disbursedLoanID, null);
        loanStatusHashMap = (HashMap) LOAN_TRANSACTION_HELPER.getLoanDetail(REQUEST_SPEC, RESPONSE_SPEC, disbursedLoanID, "status");
        LoanStatusChecker.verifyLoanAccountIsClosed(loanStatusHashMap);

        final Float floatZero = 0.0f;
        Float totalOverpaidAtEnd = (Float) LOAN_TRANSACTION_HELPER.getLoanDetail(REQUEST_SPEC, RESPONSE_SPEC, disbursedLoanID,
                "totalOverpaid");
        if (totalOverpaidAtEnd == null) {
            totalOverpaidAtEnd = floatZero;
        }
        assertEquals(totalOverpaidAtEnd, floatZero);
    }

    @ParameterizedTest
    @MethodSource("loanProductFactory")
    public void refundAcceptedOnTheCurrentBusinessDate(LoanProductTestBuilder loanProductTestBuilder) {
        runAt("09 January 2022", () -> {
            disburseLoanOfAccountingRule(ACCRUAL_PERIODIC, loanProductTestBuilder);
            HashMap loanStatusHashMap = makeRepayment("06 January 2022", 20000.00f); // overpayment
            LoanStatusChecker.verifyLoanAccountIsOverPaid(loanStatusHashMap);

            final Float totalOverpaid = (Float) LOAN_TRANSACTION_HELPER.getLoanDetail(REQUEST_SPEC, RESPONSE_SPEC, disbursedLoanID,
                    "totalOverpaid");

            final String creditBalanceRefundDate = "09 January 2022";
            final String externalId = null;
            LOAN_TRANSACTION_HELPER.creditBalanceRefund(creditBalanceRefundDate, totalOverpaid, externalId, disbursedLoanID, null);
            loanStatusHashMap = (HashMap) LOAN_TRANSACTION_HELPER.getLoanDetail(REQUEST_SPEC, RESPONSE_SPEC, disbursedLoanID, "status");
            LoanStatusChecker.verifyLoanAccountIsClosed(loanStatusHashMap);

            final Float floatZero = 0.0f;
            Float totalOverpaidAtEnd = (Float) LOAN_TRANSACTION_HELPER.getLoanDetail(REQUEST_SPEC, RESPONSE_SPEC, disbursedLoanID,
                    "totalOverpaid");
            if (totalOverpaidAtEnd == null) {
                totalOverpaidAtEnd = floatZero;
            }
            assertEquals(totalOverpaidAtEnd, floatZero);
        });
    }

    @ParameterizedTest
    @MethodSource("loanProductFactory")
    public void refundCannotBeDuneForFutureDate(LoanProductTestBuilder loanProductTestBuilder) {
        runAt("06 January 2022", () -> {
            disburseLoanOfAccountingRule(ACCRUAL_PERIODIC, loanProductTestBuilder);
            HashMap loanStatusHashMap = makeRepayment("06 January 2022", 20000.00f); // overpayment
            LoanStatusChecker.verifyLoanAccountIsOverPaid(loanStatusHashMap);

            final Float totalOverpaid = (Float) LOAN_TRANSACTION_HELPER.getLoanDetail(REQUEST_SPEC, RESPONSE_SPEC, disbursedLoanID,
                    "totalOverpaid");

            final String creditBalanceRefundDate = "09 January 2022";
            final String externalId = null;

            ArrayList<HashMap> cbrErrors = (ArrayList<HashMap>) loanTransactionHelperValidationError.creditBalanceRefund(
                    creditBalanceRefundDate, totalOverpaid, externalId, disbursedLoanID, CommonConstants.RESPONSE_ERROR);

            assertEquals("error.msg.transaction.date.cannot.be.in.the.future",
                    cbrErrors.get(0).get(CommonConstants.RESPONSE_ERROR_MESSAGE_CODE));
        });
    }

    @ParameterizedTest
    @MethodSource("loanProductFactory")
    public void partialRefundKeepsOverpaidStatusTest(LoanProductTestBuilder loanProductTestBuilder) {
        disburseLoanOfAccountingRule(ACCRUAL_PERIODIC, loanProductTestBuilder);
        HashMap loanStatusHashMap = makeRepayment("06 January 2022", 20000.00f); // overpayment
        LoanStatusChecker.verifyLoanAccountIsOverPaid(loanStatusHashMap);

        final Float refund = 5000.00f; // partial refund

        final String creditBalanceRefundDate = "09 January 2022";
        final String externalId = null;
        LOAN_TRANSACTION_HELPER.creditBalanceRefund(creditBalanceRefundDate, refund, externalId, disbursedLoanID, null);
        loanStatusHashMap = (HashMap) LOAN_TRANSACTION_HELPER.getLoanDetail(REQUEST_SPEC, RESPONSE_SPEC, disbursedLoanID, "status");
        LoanStatusChecker.verifyLoanAccountIsOverPaid(loanStatusHashMap);

    }

    @ParameterizedTest
    @MethodSource("loanProductFactory")
    public void newCreditBalanceRefundSavesExternalIdTest(LoanProductTestBuilder loanProductTestBuilder) {
        disburseLoanOfAccountingRule(ACCRUAL_PERIODIC, loanProductTestBuilder);
        HashMap loanStatusHashMap = makeRepayment("06 January 2022", 20000.00f); // overpayment
        LoanStatusChecker.verifyLoanAccountIsOverPaid(loanStatusHashMap);

        final Float refund = 1000.00f; // partial refund
        final String creditBalanceRefundDate = "09 January 2022";
        final String externalId = "cbrextID" + disbursedLoanID.toString();
        Integer resourceId = (Integer) LOAN_TRANSACTION_HELPER.creditBalanceRefund(creditBalanceRefundDate, refund, externalId,
                disbursedLoanID, "resourceId");
        Assertions.assertNotNull(resourceId);

        HashMap creditBalanceRefundMap = (HashMap) LOAN_TRANSACTION_HELPER.getLoanTransactionDetails(disbursedLoanID, resourceId, "");
        Assertions.assertNotNull(creditBalanceRefundMap.get("externalId"));
        Assertions.assertEquals(creditBalanceRefundMap.get("externalId"), externalId, "Incorrect External Id Saved");

    }

    @ParameterizedTest
    @MethodSource("loanProductFactory")
    public void newCreditBalanceRefundFindsDuplicateExternalIdTest(LoanProductTestBuilder loanProductTestBuilder) {
        disburseLoanOfAccountingRule(ACCRUAL_PERIODIC, loanProductTestBuilder);
        HashMap loanStatusHashMap = makeRepayment("06 January 2022", 20000.00f); // overpayment
        LoanStatusChecker.verifyLoanAccountIsOverPaid(loanStatusHashMap);

        final Float refund = 1000.00f; // partial refund
        final String creditBalanceRefundDate = "09 January 2022";
        final String externalId = "cbrextID" + disbursedLoanID.toString();
        final Integer resourceId = (Integer) LOAN_TRANSACTION_HELPER.creditBalanceRefund(creditBalanceRefundDate, refund, externalId,
                disbursedLoanID, "resourceId");
        Assertions.assertNotNull(resourceId);

        final Float refund2 = 10.00f; // partial refund
        final String creditBalanceRefundDate2 = "10 January 2022";
        ArrayList<HashMap> cbrErrors = (ArrayList<HashMap>) loanTransactionHelperValidationError
                .creditBalanceRefund(creditBalanceRefundDate2, refund2, externalId, disbursedLoanID, CommonConstants.RESPONSE_ERROR);
        assertEquals("error.msg.loan.creditBalanceRefund.duplicate.externalId",
                cbrErrors.get(0).get(CommonConstants.RESPONSE_ERROR_MESSAGE_CODE));

    }

    @ParameterizedTest
    @MethodSource("loanProductFactory")
    public void newCreditBalanceRefundCreatesCorrectJournalEntriesForPeriodicAccrualsTest(LoanProductTestBuilder loanProductTestBuilder) {
        disburseLoanOfAccountingRule(ACCRUAL_PERIODIC, loanProductTestBuilder);
        HashMap loanStatusHashMap = makeRepayment("06 January 2022", 20000.00f); // overpayment
        LoanStatusChecker.verifyLoanAccountIsOverPaid(loanStatusHashMap);

        final Float refund = 1000.00f; // partial refund
        final String creditBalanceRefundDate = "09 January 2022";
        final String externalId = null;
        final Integer resourceId = (Integer) LOAN_TRANSACTION_HELPER.creditBalanceRefund(creditBalanceRefundDate, refund, externalId,
                disbursedLoanID, "resourceId");
        Assertions.assertNotNull(resourceId);

        JOURNAL_ENTRY_HELPER.checkJournalEntryForAssetAccount(LOANS_RECEIVABLE_ACCOUNT, creditBalanceRefundDate,
                new JournalEntry(refund, JournalEntry.TransactionType.CREDIT));
        JOURNAL_ENTRY_HELPER.checkJournalEntryForLiabilityAccount(OVERPAYMENT_ACCOUNT, creditBalanceRefundDate,
                new JournalEntry(refund, JournalEntry.TransactionType.DEBIT));

    }

    @ParameterizedTest
    @MethodSource("loanProductFactory")
    public void newCreditBalanceRefundCreatesCorrectJournalEntriesForCashAccountingTest(LoanProductTestBuilder loanProductTestBuilder) {
        disburseLoanOfAccountingRule(CASH_BASED, loanProductTestBuilder);
        HashMap loanStatusHashMap = makeRepayment("08 January 2022", 20000.00f); // overpayment
        LoanStatusChecker.verifyLoanAccountIsOverPaid(loanStatusHashMap);

        final Float refund = 1000.00f; // partial refund
        final String creditBalanceRefundDate = "09 January 2022";
        final String externalId = null;
        final Integer resourceId = (Integer) LOAN_TRANSACTION_HELPER.creditBalanceRefund(creditBalanceRefundDate, refund, externalId,
                disbursedLoanID, "resourceId");
        Assertions.assertNotNull(resourceId);

        JOURNAL_ENTRY_HELPER.checkJournalEntryForAssetAccount(LOANS_RECEIVABLE_ACCOUNT, creditBalanceRefundDate,
                new JournalEntry(refund, JournalEntry.TransactionType.CREDIT));
        JOURNAL_ENTRY_HELPER.checkJournalEntryForLiabilityAccount(OVERPAYMENT_ACCOUNT, creditBalanceRefundDate,
                new JournalEntry(refund, JournalEntry.TransactionType.DEBIT));

    }

    @ParameterizedTest
    @MethodSource("loanProductFactory")
    public void repaymentTransactionTypeMatchesTest(LoanProductTestBuilder loanProductTestBuilder) {
        disburseLoanOfAccountingRule(ACCRUAL_PERIODIC, loanProductTestBuilder);
        verifyRepaymentTransactionTypeMatches(MERCHANT_ISSUED_REFUND);
        verifyRepaymentTransactionTypeMatches(PAYOUT_REFUND);
        verifyRepaymentTransactionTypeMatches(GOODWILL_CREDIT);

    }

    private void verifyRepaymentTransactionTypeMatches(final String repaymentTransactionType) {
        HashMap loanStatusHashMap = (HashMap) LOAN_TRANSACTION_HELPER.makeRepaymentTypePayment(repaymentTransactionType, "06 January 2022",
                200.00f, disbursedLoanID, "");
        Integer newTransactionId = (Integer) loanStatusHashMap.get("resourceId");
        loanStatusHashMap = (HashMap) LOAN_TRANSACTION_HELPER.getLoanTransactionDetails(disbursedLoanID, newTransactionId, "");

        HashMap typeMap = (HashMap) loanStatusHashMap.get("type");
        Boolean isTypeCorrect = (Boolean) typeMap.get(repaymentTransactionType);
        Assertions.assertTrue(Boolean.TRUE.equals(isTypeCorrect), "Not " + repaymentTransactionType);
    }

    @ParameterizedTest
    @MethodSource("loanProductFactory")
    public void repaymentTransactionTypeWhenPaidTest(LoanProductTestBuilder loanProductTestBuilder) {
        disburseLoanOfAccountingRule(ACCRUAL_PERIODIC, loanProductTestBuilder);
        verifyRepaymentTransactionTypeWhenPaid(MERCHANT_ISSUED_REFUND);
        verifyRepaymentTransactionTypeWhenPaid(PAYOUT_REFUND);
        verifyRepaymentTransactionTypeWhenPaid(GOODWILL_CREDIT);
        verifyRepaymentTransactionTypeWhenPaid(REPAYMENT);

    }

    private void verifyRepaymentTransactionTypeWhenPaid(final String repaymentTransactionType) {

        // Overpay loan
        Integer resourceId = (Integer) LOAN_TRANSACTION_HELPER.makeRepaymentTypePayment(REPAYMENT, "06 January 2022", 13000.00f,
                disbursedLoanID, "resourceId");
        Assertions.assertNotNull(resourceId);
        resourceId = (Integer) LOAN_TRANSACTION_HELPER.makeRepaymentTypePayment(repaymentTransactionType, "06 January 2022", 1.00f,
                disbursedLoanID, "resourceId");
        Assertions.assertNotNull(resourceId);
    }

    @ParameterizedTest
    @MethodSource("loanProductFactory")
    public void goodWillCreditWillCloseTheLoanCorrectly(LoanProductTestBuilder loanProductTestBuilder) {

        disburseLoanOfAccountingRule(ACCRUAL_PERIODIC, loanProductTestBuilder);
        HashMap loanSummaryMap = LOAN_TRANSACTION_HELPER.getLoanSummary(REQUEST_SPEC, RESPONSE_SPEC, disbursedLoanID);

        // pay off all of principal, interest (no fees or penalties)
        final Float totalOutstanding = (Float) loanSummaryMap.get("totalOutstanding");
        final Float goodwillAmount = totalOutstanding;
        final String goodwillDate = "09 March 2022";
        HashMap loanStatusHashMap = (HashMap) LOAN_TRANSACTION_HELPER.makeRepaymentTypePayment(GOODWILL_CREDIT, goodwillDate,
                goodwillAmount, disbursedLoanID, "");

        GetLoansLoanIdResponse details = LOAN_TRANSACTION_HELPER.getLoan(REQUEST_SPEC, RESPONSE_SPEC, disbursedLoanID);

        Assertions.assertNull(details.getSummary().getInArrears());
        Assertions.assertTrue(details.getStatus().getClosedObligationsMet());
    }

    @ParameterizedTest
    @MethodSource("loanProductFactory")
    public void paymentRefundWillCloseTheLoanCorrectly(LoanProductTestBuilder loanProductTestBuilder) {

        disburseLoanOfAccountingRule(ACCRUAL_PERIODIC, loanProductTestBuilder);
        HashMap loanSummaryMap = LOAN_TRANSACTION_HELPER.getLoanSummary(REQUEST_SPEC, RESPONSE_SPEC, disbursedLoanID);

        // pay off all of principal, interest (no fees or penalties)
        final Float totalOutstanding = (Float) loanSummaryMap.get("totalOutstanding");
        final Float goodwillAmount = totalOutstanding;
        final String goodwillDate = "09 March 2022";
        HashMap loanStatusHashMap = (HashMap) LOAN_TRANSACTION_HELPER.makeRepaymentTypePayment(PAYOUT_REFUND, goodwillDate, goodwillAmount,
                disbursedLoanID, "");

        GetLoansLoanIdResponse details = LOAN_TRANSACTION_HELPER.getLoan(REQUEST_SPEC, RESPONSE_SPEC, disbursedLoanID);

        Assertions.assertNull(details.getSummary().getInArrears());
        Assertions.assertTrue(details.getStatus().getClosedObligationsMet());
    }

    @ParameterizedTest
    @MethodSource("loanProductFactory")
    public void newGoodwillCreditCreatesCorrectJournalEntriesForPeriodicAccrualsTest(LoanProductTestBuilder loanProductTestBuilder) {

        disburseLoanOfAccountingRule(ACCRUAL_PERIODIC, loanProductTestBuilder);
        HashMap loanSummaryMap = LOAN_TRANSACTION_HELPER.getLoanSummary(REQUEST_SPEC, RESPONSE_SPEC, disbursedLoanID);

        // pay off all of principal, interest (no fees or penalties)
        final Float principalOutstanding = (Float) loanSummaryMap.get("principalOutstanding");
        final Float interestOutstanding = (Float) loanSummaryMap.get("interestOutstanding");
        final Float totalOutstanding = (Float) loanSummaryMap.get("totalOutstanding");
        final Float overpaidAmount = 159.00f;
        final Float goodwillAmount = totalOutstanding + overpaidAmount;
        final Float goodwillAmountInExpense = principalOutstanding + overpaidAmount;
        final String goodwillDate = "09 January 2022";
        HashMap loanStatusHashMap = (HashMap) LOAN_TRANSACTION_HELPER.makeRepaymentTypePayment(GOODWILL_CREDIT, goodwillDate,
                goodwillAmount, disbursedLoanID, "");

        // only a single credit for principal and interest as test sets up same GL account for both (summed up)
        JOURNAL_ENTRY_HELPER.checkJournalEntryForAssetAccount(LOANS_RECEIVABLE_ACCOUNT, goodwillDate,
                new JournalEntry(totalOutstanding, JournalEntry.TransactionType.CREDIT));
        JOURNAL_ENTRY_HELPER.checkJournalEntryForLiabilityAccount(OVERPAYMENT_ACCOUNT, goodwillDate,
                new JournalEntry(overpaidAmount, JournalEntry.TransactionType.CREDIT));
        JOURNAL_ENTRY_HELPER.checkJournalEntryForExpenseAccount(CHARGE_OFF_EXPENSE_ACCOUNT, goodwillDate,
                new JournalEntry(goodwillAmountInExpense, JournalEntry.TransactionType.DEBIT));

    }

    @ParameterizedTest
    @MethodSource("loanProductFactory")
    public void newGoodwillCreditCreatesCorrectJournalEntriesForCashAccountingTest(LoanProductTestBuilder loanProductTestBuilder) {

        disburseLoanOfAccountingRule(CASH_BASED, loanProductTestBuilder);
        HashMap loanSummaryMap = LOAN_TRANSACTION_HELPER.getLoanSummary(REQUEST_SPEC, RESPONSE_SPEC, disbursedLoanID);

        // pay off all of principal, interest (no fees or penalties)
        final Float principalOutstanding = (Float) loanSummaryMap.get("principalOutstanding");
        final Float interestOutstanding = (Float) loanSummaryMap.get("interestOutstanding");
        final Float totalOutstanding = (Float) loanSummaryMap.get("totalOutstanding");
        final Float overpaidAmount = 159.00f;
        final Float goodwillAmount = totalOutstanding + overpaidAmount;
        final Float goodwillAmountInExpense = principalOutstanding + overpaidAmount;
        final String goodwillDate = "09 January 2022";
        HashMap loanStatusHashMap = (HashMap) LOAN_TRANSACTION_HELPER.makeRepaymentTypePayment(GOODWILL_CREDIT, goodwillDate,
                goodwillAmount, disbursedLoanID, "");

        JOURNAL_ENTRY_HELPER.checkJournalEntryForAssetAccount(LOANS_RECEIVABLE_ACCOUNT, goodwillDate,
                new JournalEntry(principalOutstanding, JournalEntry.TransactionType.CREDIT));
        JOURNAL_ENTRY_HELPER.checkJournalEntryForIncomeAccount(FEE_INCOME_ACCOUNT, goodwillDate,
                new JournalEntry(interestOutstanding, JournalEntry.TransactionType.CREDIT));

        JOURNAL_ENTRY_HELPER.checkJournalEntryForLiabilityAccount(OVERPAYMENT_ACCOUNT, goodwillDate,
                new JournalEntry(overpaidAmount, JournalEntry.TransactionType.CREDIT));
        JOURNAL_ENTRY_HELPER.checkJournalEntryForExpenseAccount(CHARGE_OFF_EXPENSE_ACCOUNT, goodwillDate,
                new JournalEntry(goodwillAmountInExpense, JournalEntry.TransactionType.DEBIT));

    }

    @ParameterizedTest
    @MethodSource("loanProductFactory")
    public void undoGoodWillCreditTransactionTest(LoanProductTestBuilder loanProductTestBuilder) {
        // Given
        disburseLoanOfAccountingRule(CASH_BASED, loanProductTestBuilder);
        HashMap loanSummaryMap = LOAN_TRANSACTION_HELPER.getLoanSummary(REQUEST_SPEC, RESPONSE_SPEC, disbursedLoanID);

        // pay off all of principal, interest (no fees or penalties)
        final Float principalOutstanding = (Float) loanSummaryMap.get("principalOutstanding");
        final Float interestOutstanding = (Float) loanSummaryMap.get("interestOutstanding");
        final Float totalOutstanding = (Float) loanSummaryMap.get("totalOutstanding");
        final Float overpaidAmount = 159.00f;
        final Float transactionAmount = totalOutstanding + overpaidAmount;
        final String transactionDate = "09 January 2022";
        PostLoansLoanIdTransactionsResponse loanTransactionResponse = LOAN_TRANSACTION_HELPER.makeLoanRepayment(GOODWILL_CREDIT,
                transactionDate, transactionAmount, disbursedLoanID);
        Assertions.assertNotNull(loanTransactionResponse);
        Assertions.assertNotNull(loanTransactionResponse.getResourceId());

        // Then
        LOAN_TRANSACTION_HELPER.reverseLoanTransaction(disbursedLoanID, loanTransactionResponse.getResourceId(), transactionDate,
                RESPONSE_SPEC);
    }

    @ParameterizedTest
    @MethodSource("loanProductFactory")
    public void undoPayoutRefundTransactionTest(LoanProductTestBuilder loanProductTestBuilder) {
        // Given
        disburseLoanOfAccountingRule(CASH_BASED, loanProductTestBuilder);
        HashMap loanSummaryMap = LOAN_TRANSACTION_HELPER.getLoanSummary(REQUEST_SPEC, RESPONSE_SPEC, disbursedLoanID);

        // pay off all of principal, interest (no fees or penalties)
        final Float principalOutstanding = (Float) loanSummaryMap.get("principalOutstanding");
        final Float interestOutstanding = (Float) loanSummaryMap.get("interestOutstanding");
        final Float totalOutstanding = (Float) loanSummaryMap.get("totalOutstanding");
        final Float overpaidAmount = 159.00f;
        final Float transactionAmount = totalOutstanding + overpaidAmount;
        final String transactionDate = "09 January 2022";
        PostLoansLoanIdTransactionsResponse loanTransactionResponse = LOAN_TRANSACTION_HELPER.makeLoanRepayment(PAYOUT_REFUND,
                transactionDate, transactionAmount, disbursedLoanID);
        Assertions.assertNotNull(loanTransactionResponse);
        Assertions.assertNotNull(loanTransactionResponse.getResourceId());

        // Then
        LOAN_TRANSACTION_HELPER.reverseLoanTransaction(disbursedLoanID, loanTransactionResponse.getResourceId(), transactionDate,
                RESPONSE_SPEC);
    }

    @ParameterizedTest
    @MethodSource("loanProductFactory")
    public void undoMerchantIssuedRefundTransactionTest(LoanProductTestBuilder loanProductTestBuilder) {
        // Given
        disburseLoanOfAccountingRule(CASH_BASED, loanProductTestBuilder);
        HashMap loanSummaryMap = LOAN_TRANSACTION_HELPER.getLoanSummary(REQUEST_SPEC, RESPONSE_SPEC, disbursedLoanID);

        // pay off all of principal, interest (no fees or penalties)
        final Float principalOutstanding = (Float) loanSummaryMap.get("principalOutstanding");
        final Float interestOutstanding = (Float) loanSummaryMap.get("interestOutstanding");
        final Float totalOutstanding = (Float) loanSummaryMap.get("totalOutstanding");
        final Float overpaidAmount = 159.00f;
        final Float transactionAmount = totalOutstanding + overpaidAmount;
        final String transactionDate = "09 January 2022";
        PostLoansLoanIdTransactionsResponse loanTransactionResponse = LOAN_TRANSACTION_HELPER.makeLoanRepayment(MERCHANT_ISSUED_REFUND,
                transactionDate, transactionAmount, disbursedLoanID);
        Assertions.assertNotNull(loanTransactionResponse);
        Assertions.assertNotNull(loanTransactionResponse.getResourceId());

        // Then
        LOAN_TRANSACTION_HELPER.reverseLoanTransaction(disbursedLoanID, loanTransactionResponse.getResourceId(), transactionDate,
                RESPONSE_SPEC);
    }

    @ParameterizedTest
    @MethodSource("loanProductFactory")
    public void adjustGoodWillCreditTransactionTest(LoanProductTestBuilder loanProductTestBuilder) {
        // Given
        disburseLoanOfAccountingRule(CASH_BASED, loanProductTestBuilder);
        HashMap loanSummaryMap = LOAN_TRANSACTION_HELPER.getLoanSummary(REQUEST_SPEC, RESPONSE_SPEC, disbursedLoanID);

        // pay off all of principal, interest (no fees or penalties)
        final Float principalOutstanding = (Float) loanSummaryMap.get("principalOutstanding");
        final Float interestOutstanding = (Float) loanSummaryMap.get("interestOutstanding");
        final Float totalOutstanding = (Float) loanSummaryMap.get("totalOutstanding");
        final Float overpaidAmount = 159.00f;
        final Float transactionAmount = totalOutstanding + overpaidAmount;
        final String transactionDate = "09 January 2022";
        PostLoansLoanIdTransactionsResponse loanTransactionResponse = LOAN_TRANSACTION_HELPER.makeLoanRepayment(GOODWILL_CREDIT,
                transactionDate, transactionAmount, disbursedLoanID);
        Assertions.assertNotNull(loanTransactionResponse);
        Assertions.assertNotNull(loanTransactionResponse.getResourceId());

        // Then
        LOAN_TRANSACTION_HELPER.adjustLoanTransaction(disbursedLoanID, loanTransactionResponse.getResourceId(), transactionDate,
                RESPONSE_SPEC_403);
    }

    @ParameterizedTest
    @MethodSource("loanProductFactory")
    public void adjustPayoutRefundTransactionTest(LoanProductTestBuilder loanProductTestBuilder) {
        // Given
        disburseLoanOfAccountingRule(CASH_BASED, loanProductTestBuilder);
        HashMap loanSummaryMap = LOAN_TRANSACTION_HELPER.getLoanSummary(REQUEST_SPEC, RESPONSE_SPEC, disbursedLoanID);

        // pay off all of principal, interest (no fees or penalties)
        final Float principalOutstanding = (Float) loanSummaryMap.get("principalOutstanding");
        final Float interestOutstanding = (Float) loanSummaryMap.get("interestOutstanding");
        final Float totalOutstanding = (Float) loanSummaryMap.get("totalOutstanding");
        final Float overpaidAmount = 159.00f;
        final Float transactionAmount = totalOutstanding + overpaidAmount;
        final String transactionDate = "09 January 2022";
        PostLoansLoanIdTransactionsResponse loanTransactionResponse = LOAN_TRANSACTION_HELPER.makeLoanRepayment(PAYOUT_REFUND,
                transactionDate, transactionAmount, disbursedLoanID);
        Assertions.assertNotNull(loanTransactionResponse);
        Assertions.assertNotNull(loanTransactionResponse.getResourceId());

        // Then
        LOAN_TRANSACTION_HELPER.adjustLoanTransaction(disbursedLoanID, loanTransactionResponse.getResourceId(), transactionDate,
                RESPONSE_SPEC_403);
    }

    @ParameterizedTest
    @MethodSource("loanProductFactory")
    public void adjustMerchantIssuedRefundTransactionTest(LoanProductTestBuilder loanProductTestBuilder) {
        // Given
        disburseLoanOfAccountingRule(CASH_BASED, loanProductTestBuilder);
        HashMap loanSummaryMap = LOAN_TRANSACTION_HELPER.getLoanSummary(REQUEST_SPEC, RESPONSE_SPEC, disbursedLoanID);

        // pay off all of principal, interest (no fees or penalties)
        final Float principalOutstanding = (Float) loanSummaryMap.get("principalOutstanding");
        final Float interestOutstanding = (Float) loanSummaryMap.get("interestOutstanding");
        final Float totalOutstanding = (Float) loanSummaryMap.get("totalOutstanding");
        final Float overpaidAmount = 159.00f;
        final Float transactionAmount = totalOutstanding + overpaidAmount;
        final String transactionDate = "09 January 2022";
        PostLoansLoanIdTransactionsResponse loanTransactionResponse = LOAN_TRANSACTION_HELPER.makeLoanRepayment(MERCHANT_ISSUED_REFUND,
                transactionDate, transactionAmount, disbursedLoanID);
        Assertions.assertNotNull(loanTransactionResponse);
        Assertions.assertNotNull(loanTransactionResponse.getResourceId());

        // Then
        LOAN_TRANSACTION_HELPER.adjustLoanTransaction(disbursedLoanID, loanTransactionResponse.getResourceId(), transactionDate,
                RESPONSE_SPEC_403);
    }

    @Test
    public void cbrReverseReplayTest() {
        runAt("06 March 2024", () -> {
            Long clientId = CLIENT_HELPER.createClient(ClientHelper.defaultClientCreationRequest()).getClientId();
            PostLoanProductsRequest product = createOnePeriod30DaysLongNoInterestPeriodicAccrualProduct().numberOfRepayments(1)
                    .repaymentEvery(30).enableDownPayment(false);
            PostLoanProductsResponse loanProductResponse = LOAN_PRODUCT_HELPER.createLoanProduct(product);
            PostLoansRequest applicationRequest = applyLoanRequest(clientId, loanProductResponse.getResourceId(), "25 January 2024", 1000.0,
                    4);

            applicationRequest = applicationRequest.numberOfRepayments(1).loanTermFrequency(30)
                    .transactionProcessingStrategyCode(
                            LoanProductTestBuilder.DUE_PENALTY_FEE_INTEREST_PRINCIPAL_IN_ADVANCE_PRINCIPAL_PENALTY_FEE_INTEREST_STRATEGY)
                    .repaymentEvery(30);

            PostLoansResponse loanResponse = LOAN_TRANSACTION_HELPER.applyLoan(applicationRequest);

            LOAN_TRANSACTION_HELPER.approveLoan(loanResponse.getLoanId(),
                    new PostLoansLoanIdRequest().approvedLoanAmount(BigDecimal.valueOf(1000)).dateFormat(CommonConstants.DATE_FORMAT)
                            .approvedOnDate("25 January 2024").locale("en"));

            LOAN_TRANSACTION_HELPER.disburseLoan(loanResponse.getLoanId(),
                    new PostLoansLoanIdRequest().actualDisbursementDate("25 January 2024").dateFormat(CommonConstants.DATE_FORMAT)
                            .transactionAmount(BigDecimal.valueOf(100.0)).locale("en"));

            GetLoansLoanIdResponse loanDetails = LOAN_TRANSACTION_HELPER.getLoanDetails(loanResponse.getLoanId());
            validateLoanSummaryBalances(loanDetails, 100.0, 0.0, 100.0, 0.0, null);
            validateRepaymentPeriod(loanDetails, 1, LocalDate.of(2024, 2, 24), 100.0, 0.0, 100.0, 0.0, 0.0);
            assertTrue(loanDetails.getStatus().getActive());

            String repaymentExternalId = UUID.randomUUID().toString();
            LOAN_TRANSACTION_HELPER.makeLoanRepayment(loanResponse.getLoanId(),
                    new PostLoansLoanIdTransactionsRequest().dateFormat(CommonConstants.DATE_FORMAT).transactionDate("24 February 2024")
                            .locale("en").transactionAmount(100.0).externalId(repaymentExternalId));
            loanDetails = LOAN_TRANSACTION_HELPER.getLoanDetails(loanResponse.getLoanId());
            validateLoanSummaryBalances(loanDetails, 0.0, 100.0, 0.0, 100.0, null);
            validateRepaymentPeriod(loanDetails, 1, LocalDate.of(2024, 2, 24), 100.0, 100.0, 0.0, 0.0, 0.0);
            assertTrue(loanDetails.getStatus().getClosedObligationsMet());

            String mir1ExternalId = UUID.randomUUID().toString();
            LOAN_TRANSACTION_HELPER.makeMerchantIssuedRefund(loanResponse.getLoanId(),
                    new PostLoansLoanIdTransactionsRequest().transactionDate("28 February 2024").dateFormat(CommonConstants.DATE_FORMAT)
                            .transactionAmount(36.99).locale("en").externalId(mir1ExternalId));
            loanDetails = LOAN_TRANSACTION_HELPER.getLoanDetails(loanResponse.getLoanId());
            validateLoanSummaryBalances(loanDetails, 0.0, 100.0, 0.0, 100.0, 36.99);
            validateRepaymentPeriod(loanDetails, 1, LocalDate.of(2024, 2, 24), 100.0, 100.0, 0.0, 0.0, 0.0);
            assertTrue(loanDetails.getStatus().getOverpaid());

            LOAN_TRANSACTION_HELPER.makeMerchantIssuedRefund(loanResponse.getLoanId(), new PostLoansLoanIdTransactionsRequest()
                    .transactionDate("28 February 2024").dateFormat(CommonConstants.DATE_FORMAT).transactionAmount(18.94).locale("en"));
            loanDetails = LOAN_TRANSACTION_HELPER.getLoanDetails(loanResponse.getLoanId());
            validateLoanSummaryBalances(loanDetails, 0.0, 100.0, 0.0, 100.0, 55.93);
            validateRepaymentPeriod(loanDetails, 1, LocalDate.of(2024, 2, 24), 100.0, 100.0, 0.0, 0.0, 0.0);
            assertTrue(loanDetails.getStatus().getOverpaid());

            LOAN_TRANSACTION_HELPER.makeMerchantIssuedRefund(loanResponse.getLoanId(), new PostLoansLoanIdTransactionsRequest()
                    .transactionDate("28 February 2024").dateFormat(CommonConstants.DATE_FORMAT).transactionAmount(36.99).locale("en"));
            loanDetails = LOAN_TRANSACTION_HELPER.getLoanDetails(loanResponse.getLoanId());
            validateLoanSummaryBalances(loanDetails, 0.0, 100.0, 0.0, 100.0, 92.92);
            validateRepaymentPeriod(loanDetails, 1, LocalDate.of(2024, 2, 24), 100.0, 100.0, 0.0, 0.0, 0.0);
            assertTrue(loanDetails.getStatus().getOverpaid());

            LOAN_TRANSACTION_HELPER.makeMerchantIssuedRefund(loanResponse.getLoanId(), new PostLoansLoanIdTransactionsRequest()
                    .transactionDate("28 February 2024").dateFormat(CommonConstants.DATE_FORMAT).transactionAmount(31.99).locale("en"));
            loanDetails = LOAN_TRANSACTION_HELPER.getLoanDetails(loanResponse.getLoanId());
            validateLoanSummaryBalances(loanDetails, 0.0, 100.0, 0.0, 100.0, 124.91);
            validateRepaymentPeriod(loanDetails, 1, LocalDate.of(2024, 2, 24), 100.0, 100.0, 0.0, 0.0, 0.0);
            assertTrue(loanDetails.getStatus().getOverpaid());

            LOAN_TRANSACTION_HELPER.makeCreditBalanceRefund(loanResponse.getLoanId(), new PostLoansLoanIdTransactionsRequest()
                    .transactionDate("01 March 2024").dateFormat(CommonConstants.DATE_FORMAT).transactionAmount(124.91).locale("en"));
            loanDetails = LOAN_TRANSACTION_HELPER.getLoanDetails(loanResponse.getLoanId());
            validateLoanSummaryBalances(loanDetails, 0.0, 100.0, 0.0, 100.0, null);
            validateRepaymentPeriod(loanDetails, 1, LocalDate.of(2024, 2, 24), 100.0, 100.0, 0.0, 0.0, 0.0);
            assertTrue(loanDetails.getStatus().getClosedObligationsMet());

            LOAN_TRANSACTION_HELPER.makeMerchantIssuedRefund(loanResponse.getLoanId(), new PostLoansLoanIdTransactionsRequest()
                    .transactionDate("02 March 2024").dateFormat(CommonConstants.DATE_FORMAT).transactionAmount(19.99).locale("en"));
            loanDetails = LOAN_TRANSACTION_HELPER.getLoanDetails(loanResponse.getLoanId());
            validateLoanSummaryBalances(loanDetails, 0.0, 100.0, 0.0, 100.0, 19.99);
            validateRepaymentPeriod(loanDetails, 1, LocalDate.of(2024, 2, 24), 100.0, 100.0, 0.0, 0.0, 0.0);
            assertTrue(loanDetails.getStatus().getOverpaid());

            LOAN_TRANSACTION_HELPER.makeMerchantIssuedRefund(loanResponse.getLoanId(), new PostLoansLoanIdTransactionsRequest()
                    .transactionDate("02 March 2024").dateFormat(CommonConstants.DATE_FORMAT).transactionAmount(19.99).locale("en"));
            loanDetails = LOAN_TRANSACTION_HELPER.getLoanDetails(loanResponse.getLoanId());
            validateLoanSummaryBalances(loanDetails, 0.0, 100.0, 0.0, 100.0, 39.98);
            validateRepaymentPeriod(loanDetails, 1, LocalDate.of(2024, 2, 24), 100.0, 100.0, 0.0, 0.0, 0.0);
            assertTrue(loanDetails.getStatus().getOverpaid());

            verifyTransactions(loanResponse.getLoanId(), //
                    transaction(100, "Disbursement", "25 January 2024", 100.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0), //
                    transaction(100, "Repayment", "24 February 2024", 0.0, 100.0, 0.0, 0.0, 0.0, 0.0, 0.0), //
                    transaction(18.94, "Merchant Issued Refund", "28 February 2024", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 18.94), //
                    transaction(36.99, "Merchant Issued Refund", "28 February 2024", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 36.99), //
                    transaction(36.99, "Merchant Issued Refund", "28 February 2024", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 36.99), //
                    transaction(31.99, "Merchant Issued Refund", "28 February 2024", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 31.99), //
                    transaction(124.91, "Credit Balance Refund", "01 March 2024", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 124.91), //
                    transaction(19.99, "Merchant Issued Refund", "02 March 2024", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 19.99), //
                    transaction(19.99, "Merchant Issued Refund", "02 March 2024", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 19.99) //
            );

            LOAN_TRANSACTION_HELPER.reverseLoanTransaction(loanResponse.getLoanId(), mir1ExternalId,
                    new PostLoansLoanIdTransactionsTransactionIdRequest().dateFormat(CommonConstants.DATE_FORMAT)
                            .transactionDate("02 March 2024").transactionAmount(0.0).locale("en"));

            loanDetails = LOAN_TRANSACTION_HELPER.getLoanDetails(loanResponse.getLoanId());
            validateLoanSummaryBalances(loanDetails, 0.0, 224.91, 0.0, 224.91, 2.99);
            validateRepaymentPeriod(loanDetails, 1, LocalDate.of(2024, 2, 24), 100.0, 100.0, 0.0, 0.0, 0.0);
            validateRepaymentPeriod(loanDetails, 2, LocalDate.of(2024, 3, 1), 124.91, 124.91, 0.0, 0.0, 36.99);
            assertTrue(loanDetails.getStatus().getOverpaid());

            verifyTransactions(loanResponse.getLoanId(), //
                    transaction(100, "Disbursement", "25 January 2024", 100.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0), //
                    transaction(100, "Repayment", "24 February 2024", 0.0, 100.0, 0.0, 0.0, 0.0, 0.0, 0.0), //
                    transaction(36.99, "Merchant Issued Refund", "28 February 2024", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 36.99, true), //
                    transaction(18.94, "Merchant Issued Refund", "28 February 2024", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 18.94), //
                    transaction(36.99, "Merchant Issued Refund", "28 February 2024", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 36.99), //
                    transaction(31.99, "Merchant Issued Refund", "28 February 2024", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 31.99), //
                    transaction(124.91, "Credit Balance Refund", "01 March 2024", 36.99, 36.99, 0.0, 0.0, 0.0, 0.0, 87.92), //
                    transaction(19.99, "Merchant Issued Refund", "02 March 2024", 17.0, 19.99, 0.0, 0.0, 0.0, 0.0, 0.0), //
                    transaction(19.99, "Merchant Issued Refund", "02 March 2024", 0.0, 17.0, 0.0, 0.0, 0.0, 0.0, 2.99) //
            );

            LOAN_TRANSACTION_HELPER.chargebackLoanTransaction(loanResponse.getLoanId(), repaymentExternalId,
                    new PostLoansLoanIdTransactionsTransactionIdRequest().locale("en").transactionAmount(2.0));

            loanDetails = LOAN_TRANSACTION_HELPER.getLoanDetails(loanResponse.getLoanId());
            validateLoanSummaryBalances(loanDetails, 0.0, 224.91, 0.0, 224.91, 0.99);
            validateRepaymentPeriod(loanDetails, 1, LocalDate.of(2024, 2, 24), 100.0, 100.0, 0.0, 0.0, 0.0);
            validateRepaymentPeriod(loanDetails, 2, LocalDate.of(2024, 3, 1), 124.91, 124.91, 0.0, 0.0, 36.99);
            assertTrue(loanDetails.getStatus().getOverpaid());

            verifyTransactions(loanResponse.getLoanId(), //
                    transaction(100, "Disbursement", "25 January 2024", 100.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0), //
                    transaction(100, "Repayment", "24 February 2024", 0.0, 100.0, 0.0, 0.0, 0.0, 0.0, 0.0), //
                    transaction(36.99, "Merchant Issued Refund", "28 February 2024", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 36.99, true), //
                    transaction(18.94, "Merchant Issued Refund", "28 February 2024", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 18.94), //
                    transaction(36.99, "Merchant Issued Refund", "28 February 2024", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 36.99), //
                    transaction(31.99, "Merchant Issued Refund", "28 February 2024", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 31.99), //
                    transaction(124.91, "Credit Balance Refund", "01 March 2024", 36.99, 36.99, 0.0, 0.0, 0.0, 0.0, 87.92), //
                    transaction(19.99, "Merchant Issued Refund", "02 March 2024", 17.0, 19.99, 0.0, 0.0, 0.0, 0.0, 0.0), //
                    transaction(19.99, "Merchant Issued Refund", "02 March 2024", 0.0, 17.0, 0.0, 0.0, 0.0, 0.0, 2.99), //
                    transaction(2.0, "Chargeback", "06 March 2024", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 2.0) //
            );

            LOAN_TRANSACTION_HELPER.chargebackLoanTransaction(loanResponse.getLoanId(), repaymentExternalId,
                    new PostLoansLoanIdTransactionsTransactionIdRequest().locale("en").transactionAmount(1.0));

            loanDetails = LOAN_TRANSACTION_HELPER.getLoanDetails(loanResponse.getLoanId());
            validateLoanSummaryBalances(loanDetails, 0.01, 225.90, 0.01, 225.90, null);
            validateRepaymentPeriod(loanDetails, 1, LocalDate.of(2024, 2, 24), 100.0, 100.0, 0.0, 0.0, 0.0);
            validateRepaymentPeriod(loanDetails, 2, LocalDate.of(2024, 3, 6), 125.91, 125.90, 0.01, 0.0, 36.99);
            assertTrue(loanDetails.getStatus().getActive());

            verifyTransactions(loanResponse.getLoanId(), //
                    transaction(100, "Disbursement", "25 January 2024", 100.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0), //
                    transaction(100, "Repayment", "24 February 2024", 0.0, 100.0, 0.0, 0.0, 0.0, 0.0, 0.0), //
                    transaction(36.99, "Merchant Issued Refund", "28 February 2024", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 36.99, true), //
                    transaction(18.94, "Merchant Issued Refund", "28 February 2024", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 18.94), //
                    transaction(36.99, "Merchant Issued Refund", "28 February 2024", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 36.99), //
                    transaction(31.99, "Merchant Issued Refund", "28 February 2024", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 31.99), //
                    transaction(124.91, "Credit Balance Refund", "01 March 2024", 36.99, 36.99, 0.0, 0.0, 0.0, 0.0, 87.92), //
                    transaction(19.99, "Merchant Issued Refund", "02 March 2024", 17.0, 19.99, 0.0, 0.0, 0.0, 0.0, 0.0), //
                    transaction(19.99, "Merchant Issued Refund", "02 March 2024", 0.0, 17.0, 0.0, 0.0, 0.0, 0.0, 2.99), //
                    transaction(2.0, "Chargeback", "06 March 2024", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 2.0), //
                    transaction(1.0, "Chargeback", "06 March 2024", 0.01, 0.01, 0.0, 0.0, 0.0, 0.0, 0.99) //
            );

        });
    }

}
