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

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType.BUSINESS_DATE;
import static org.apache.fineract.integrationtests.common.loans.LoanApplicationTestBuilder.DUE_PENALTY_INTEREST_PRINCIPAL_FEE_IN_ADVANCE_PENALTY_INTEREST_PRINCIPAL_FEE_STRATEGY;
import static org.apache.fineract.integrationtests.common.loans.LoanProductTestBuilder.DEFAULT_STRATEGY;
import static org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.impl.AdvancedPaymentScheduleTransactionProcessor.ADVANCED_PAYMENT_ALLOCATION_STRATEGY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.internal.RequestSpecificationImpl;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.apache.fineract.batch.domain.BatchRequest;
import org.apache.fineract.batch.domain.BatchResponse;
import org.apache.fineract.client.models.AdvancedPaymentData;
import org.apache.fineract.client.models.AllowAttributeOverrides;
import org.apache.fineract.client.models.BusinessDateRequest;
import org.apache.fineract.client.models.GetJournalEntriesTransactionIdResponse;
import org.apache.fineract.client.models.GetLoansLoanIdLoanInstallmentLevelDelinquency;
import org.apache.fineract.client.models.GetLoansLoanIdRepaymentPeriod;
import org.apache.fineract.client.models.GetLoansLoanIdResponse;
import org.apache.fineract.client.models.GetLoansLoanIdTransactions;
import org.apache.fineract.client.models.PaymentAllocationOrder;
import org.apache.fineract.client.models.PostChargesResponse;
import org.apache.fineract.client.models.PostLoanProductsRequest;
import org.apache.fineract.client.models.PostLoansLoanIdChargesResponse;
import org.apache.fineract.client.models.PostLoansLoanIdRequest;
import org.apache.fineract.client.models.PostLoansLoanIdResponse;
import org.apache.fineract.client.models.PostLoansLoanIdTransactionsRequest;
import org.apache.fineract.client.models.PostLoansLoanIdTransactionsResponse;
import org.apache.fineract.client.models.PostLoansLoanIdTransactionsTransactionIdRequest;
import org.apache.fineract.client.models.PostLoansRequest;
import org.apache.fineract.client.models.PostLoansResponse;
import org.apache.fineract.client.models.PutLoansLoanIdResponse;
import org.apache.fineract.client.util.CallFailedRuntimeException;
import org.apache.fineract.integrationtests.common.BatchHelper;
import org.apache.fineract.integrationtests.common.BusinessDateHelper;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.ExternalAssetOwnerHelper;
import org.apache.fineract.integrationtests.common.GlobalConfigurationHelper;
import org.apache.fineract.integrationtests.common.LoanRescheduleRequestHelper;
import org.apache.fineract.integrationtests.common.PaymentTypeHelper;
import org.apache.fineract.integrationtests.common.SchedulerJobHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.accounting.Account;
import org.apache.fineract.integrationtests.common.accounting.AccountHelper;
import org.apache.fineract.integrationtests.common.accounting.FinancialActivityAccountHelper;
import org.apache.fineract.integrationtests.common.accounting.JournalEntryHelper;
import org.apache.fineract.integrationtests.common.charges.ChargesHelper;
import org.apache.fineract.integrationtests.common.error.ErrorResponse;
import org.apache.fineract.integrationtests.common.loans.LoanAccountLockHelper;
import org.apache.fineract.integrationtests.common.loans.LoanProductHelper;
import org.apache.fineract.integrationtests.common.loans.LoanProductTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanTestLifecycleExtension;
import org.apache.fineract.integrationtests.common.loans.LoanTransactionHelper;
import org.apache.fineract.integrationtests.common.system.CodeHelper;
import org.apache.fineract.integrationtests.inlinecob.InlineLoanCOBHelper;
import org.apache.fineract.integrationtests.useradministration.users.UserHelper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleProcessingType;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleType;
import org.apache.fineract.portfolio.loanproduct.domain.PaymentAllocationType;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.provider.Arguments;

@ExtendWith(LoanTestLifecycleExtension.class)
public abstract class BaseLoanIntegrationTest {

    protected static final String DATE_PATTERN = "dd MMMM yyyy";
    protected static final ResponseSpecification RESPONSE_SPEC = createResponseSpecification(Matchers.is(200));
    protected static final ResponseSpecification RESPONSE_SPEC_204 = createResponseSpecification(Matchers.is(204));
    protected static final ResponseSpecification RESPONSE_SPEC_400 = createResponseSpecification(Matchers.is(400));
    protected static final ResponseSpecification RESPONSE_SPEC_403 = createResponseSpecification(Matchers.is(403));
    protected static final ResponseSpecification RESPONSE_SPEC_503 = createResponseSpecification(Matchers.is(503));
    // Helpers
    protected static final LoanProductHelper LOAN_PRODUCT_HELPER = new LoanProductHelper();
    protected static final BusinessDateHelper BUSINESS_DATE_HELPER = new BusinessDateHelper();
    protected static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(DATE_PATTERN);
    private static final String FULL_ADMIN_AUTH_KEY = getFullAdminAuthKey();
    protected static final RequestSpecification REQUEST_SPEC = createRequestSpecification(FULL_ADMIN_AUTH_KEY);
    protected static final AccountHelper ACCOUNT_HELPER = new AccountHelper(REQUEST_SPEC, RESPONSE_SPEC);

    protected static final LoanTransactionHelper LOAN_TRANSACTION_HELPER = new LoanTransactionHelper(REQUEST_SPEC, RESPONSE_SPEC);
    protected static final JournalEntryHelper JOURNAL_ENTRY_HELPER = new JournalEntryHelper(REQUEST_SPEC, RESPONSE_SPEC);
    protected static final ClientHelper CLIENT_HELPER = new ClientHelper(REQUEST_SPEC, RESPONSE_SPEC);
    protected static final SchedulerJobHelper SCHEDULER_JOB_HELPER = new SchedulerJobHelper(REQUEST_SPEC);
    protected static final InlineLoanCOBHelper INLINE_LOAN_COB_HELPER = new InlineLoanCOBHelper(REQUEST_SPEC, RESPONSE_SPEC);
    protected static final LoanAccountLockHelper LOAN_ACCOUNT_LOCK_HELPER = new LoanAccountLockHelper(REQUEST_SPEC,
            createResponseSpecification(Matchers.is(202)));
    private static final String NON_BY_PASS_USER_AUTH_KEY = getNonByPassUserAuthKey(REQUEST_SPEC, RESPONSE_SPEC);
    protected static final LoanRescheduleRequestHelper LOAN_RESCHEDULE_REQUEST_HELPER = new LoanRescheduleRequestHelper(REQUEST_SPEC,
            RESPONSE_SPEC);
    protected static final PaymentTypeHelper PAYMENT_TYPE_HELPER = new PaymentTypeHelper();
    protected static final FinancialActivityAccountHelper FINANCIAL_ACTIVITY_ACCOUNT_HELPER = new FinancialActivityAccountHelper(
            REQUEST_SPEC);
    protected static final ExternalAssetOwnerHelper EXTERNAL_ASSET_OWNER_HELPER = new ExternalAssetOwnerHelper();

    // asset
    protected static final Account LOANS_RECEIVABLE_ACCOUNT = ACCOUNT_HELPER.createAssetAccount("loanPortfolio");
    protected static final Account INTEREST_RECEIVABLE_ACCOUNT = ACCOUNT_HELPER.createAssetAccount("interestReceivable");
    protected static final Account FEE_RECEIVABLE_ACCOUNT = ACCOUNT_HELPER.createAssetAccount("feeReceivable");
    protected static final Account PENALTY_RECEIVABLE_ACCOUNT = ACCOUNT_HELPER.createAssetAccount("penaltyReceivable");
    protected static final Account SUSPENSE_ACCOUNT = ACCOUNT_HELPER.createAssetAccount("suspense");
    // liability
    protected static final Account FUND_SOURCE = ACCOUNT_HELPER.createLiabilityAccount("fundSource");
    protected static final Account OVERPAYMENT_ACCOUNT = ACCOUNT_HELPER.createLiabilityAccount("overpayment");
    // income
    protected static final Account INTEREST_INCOME_ACCOUNT = ACCOUNT_HELPER.createIncomeAccount("interestIncome");
    protected static final Account FEE_INCOME_ACCOUNT = ACCOUNT_HELPER.createIncomeAccount("feeIncome");
    protected static final Account PENALTY_INCOME_ACCOUNT = ACCOUNT_HELPER.createIncomeAccount("penaltyIncome");
    protected static final Account FEE_CHARGE_OFF_ACCOUNT = ACCOUNT_HELPER.createIncomeAccount("feeChargeOff");
    protected static final Account PENALTY_CHARGE_OFF_ACCOUNT = ACCOUNT_HELPER.createIncomeAccount("penaltyChargeOff");
    protected static final Account RECOVERIES_ACCOUNT = ACCOUNT_HELPER.createIncomeAccount("recoveries");
    protected static final Account INTEREST_INCOME_CHARGE_OFF_ACCOUNT = ACCOUNT_HELPER.createIncomeAccount("interestIncomeChargeOff");
    // expense
    protected static final Account CHARGE_OFF_EXPENSE_ACCOUNT = ACCOUNT_HELPER.createExpenseAccount("chargeOff");
    protected static final Account CHARGE_OFF_FRAUD_EXPENSE_ACCOUNT = ACCOUNT_HELPER.createExpenseAccount("chargeOffFraud");
    protected static final Account WRITTEN_OFF_ACCOUNT = ACCOUNT_HELPER.createExpenseAccount();
    protected static final Account GOODWILL_EXPENSE_ACCOUNT = ACCOUNT_HELPER.createExpenseAccount();

    static {
        Utils.initializeRESTAssured();
    }

    protected static List<PaymentAllocationOrder> getPaymentAllocationOrder(PaymentAllocationType... paymentAllocationTypes) {
        AtomicInteger integer = new AtomicInteger(1);
        return Arrays.stream(paymentAllocationTypes).map(pat -> {
            PaymentAllocationOrder paymentAllocationOrder = new PaymentAllocationOrder();
            paymentAllocationOrder.setPaymentAllocationRule(pat.name());
            paymentAllocationOrder.setOrder(integer.getAndIncrement());
            return paymentAllocationOrder;
        }).toList();
    }

    protected static AdvancedPaymentData createDefaultPaymentAllocation() {
        return createDefaultPaymentAllocation("NEXT_INSTALLMENT");
    }

    protected static AdvancedPaymentData createDefaultPaymentAllocation(String futureInstallmentAllocationRule) {
        AdvancedPaymentData advancedPaymentData = new AdvancedPaymentData();
        advancedPaymentData.setTransactionType("DEFAULT");
        advancedPaymentData.setFutureInstallmentAllocationRule(futureInstallmentAllocationRule);

        List<PaymentAllocationOrder> paymentAllocationOrders = getPaymentAllocationOrder(PaymentAllocationType.PAST_DUE_PENALTY,
                PaymentAllocationType.PAST_DUE_FEE, PaymentAllocationType.PAST_DUE_PRINCIPAL, PaymentAllocationType.PAST_DUE_INTEREST,
                PaymentAllocationType.DUE_PENALTY, PaymentAllocationType.DUE_FEE, PaymentAllocationType.DUE_PRINCIPAL,
                PaymentAllocationType.DUE_INTEREST, PaymentAllocationType.IN_ADVANCE_PENALTY, PaymentAllocationType.IN_ADVANCE_FEE,
                PaymentAllocationType.IN_ADVANCE_PRINCIPAL, PaymentAllocationType.IN_ADVANCE_INTEREST);

        advancedPaymentData.setPaymentAllocationOrder(paymentAllocationOrders);
        return advancedPaymentData;
    }

    protected static AdvancedPaymentData createPaymentAllocation(String transactionType, String futureInstallmentAllocationRule) {
        AdvancedPaymentData advancedPaymentData = new AdvancedPaymentData();
        advancedPaymentData.setTransactionType(transactionType);
        advancedPaymentData.setFutureInstallmentAllocationRule(futureInstallmentAllocationRule);

        List<PaymentAllocationOrder> paymentAllocationOrders = getPaymentAllocationOrder(PaymentAllocationType.PAST_DUE_PENALTY,
                PaymentAllocationType.PAST_DUE_FEE, PaymentAllocationType.PAST_DUE_PRINCIPAL, PaymentAllocationType.PAST_DUE_INTEREST,
                PaymentAllocationType.DUE_PENALTY, PaymentAllocationType.DUE_FEE, PaymentAllocationType.DUE_PRINCIPAL,
                PaymentAllocationType.DUE_INTEREST, PaymentAllocationType.IN_ADVANCE_PENALTY, PaymentAllocationType.IN_ADVANCE_FEE,
                PaymentAllocationType.IN_ADVANCE_PRINCIPAL, PaymentAllocationType.IN_ADVANCE_INTEREST);

        advancedPaymentData.setPaymentAllocationOrder(paymentAllocationOrders);
        return advancedPaymentData;
    }

    protected static void validateRepaymentPeriod(GetLoansLoanIdResponse loanDetails, Integer index, LocalDate dueDate, double principalDue,
            double principalPaid, double principalOutstanding, double paidInAdvance, double paidLate) {
        GetLoansLoanIdRepaymentPeriod period = loanDetails.getRepaymentSchedule().getPeriods().stream()
                .filter(p -> Objects.equals(p.getPeriod(), index)).findFirst().orElseThrow();
        assertEquals(dueDate, period.getDueDate());
        assertEquals(principalDue, period.getPrincipalDue());
        assertEquals(principalPaid, period.getPrincipalPaid());
        assertEquals(principalOutstanding, period.getPrincipalOutstanding());
        assertEquals(paidInAdvance, period.getTotalPaidInAdvanceForPeriod());
        assertEquals(paidLate, period.getTotalPaidLateForPeriod());
    }

    protected static void validateRepaymentPeriod(GetLoansLoanIdResponse loanDetails, Integer index, double principalDue,
            double principalPaid, double principalOutstanding, double paidInAdvance, double paidLate) {
        GetLoansLoanIdRepaymentPeriod period = loanDetails.getRepaymentSchedule().getPeriods().stream()
                .filter(p -> Objects.equals(p.getPeriod(), index)).findFirst().orElseThrow();
        assertEquals(principalDue, period.getPrincipalDue());
        assertEquals(principalPaid, period.getPrincipalPaid());
        assertEquals(principalOutstanding, period.getPrincipalOutstanding());
        assertEquals(paidInAdvance, period.getTotalPaidInAdvanceForPeriod());
        assertEquals(paidLate, period.getTotalPaidLateForPeriod());
    }

    protected static void validateRepaymentPeriod(GetLoansLoanIdResponse loanDetails, Integer index, LocalDate dueDate, double principalDue,
            double principalPaid, double principalOutstanding, double feeDue, double feePaid, double feeOutstanding, double penaltyDue,
            double penaltyPaid, double penaltyOutstanding, double interestDue, double interestPaid, double interestOutstanding,
            double paidInAdvance, double paidLate) {
        GetLoansLoanIdRepaymentPeriod period = loanDetails.getRepaymentSchedule().getPeriods().stream()
                .filter(p -> Objects.equals(p.getPeriod(), index)).findFirst().orElseThrow();
        assertEquals(dueDate, period.getDueDate());
        assertEquals(principalDue, period.getPrincipalDue());
        assertEquals(principalPaid, period.getPrincipalPaid());
        assertEquals(principalOutstanding, period.getPrincipalOutstanding());
        assertEquals(feeDue, period.getFeeChargesDue());
        assertEquals(feePaid, period.getFeeChargesPaid());
        assertEquals(feeOutstanding, period.getFeeChargesOutstanding());
        assertEquals(penaltyDue, period.getPenaltyChargesDue());
        assertEquals(penaltyPaid, period.getPenaltyChargesPaid());
        assertEquals(penaltyOutstanding, period.getPenaltyChargesOutstanding());
        assertEquals(interestDue, period.getInterestDue());
        assertEquals(interestPaid, period.getInterestPaid());
        assertEquals(interestOutstanding, period.getInterestOutstanding());
        assertEquals(paidInAdvance, period.getTotalPaidInAdvanceForPeriod());
        assertEquals(paidLate, period.getTotalPaidLateForPeriod());
    }

    private static String getNonByPassUserAuthKey(RequestSpecification requestSpec, ResponseSpecification responseSpec) {
        // creates the user
        UserHelper.getSimpleUserWithoutBypassPermission(requestSpec, responseSpec);
        return Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey(UserHelper.SIMPLE_USER_NAME, UserHelper.SIMPLE_USER_PASSWORD);
    }

    private static String getFullAdminAuthKey() {
        return Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey();
    }

    private static RequestSpecification createRequestSpecification(String authKey) {
        RequestSpecification requestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        requestSpec.header("Authorization", "Basic " + authKey);
        requestSpec.header("Fineract-Platform-TenantId", "default");
        return requestSpec;
    }

    protected static ResponseSpecification createResponseSpecification(Matcher<Integer> statusCodeMatcher) {
        return new ResponseSpecBuilder().expectStatusCode(statusCodeMatcher).build();
    }

    // Loan product with proper accounting setup
    protected PostLoanProductsRequest createOnePeriod30DaysLongNoInterestPeriodicAccrualProduct() {
        return new PostLoanProductsRequest().name(Utils.uniqueRandomStringGenerator("LOAN_PRODUCT_", 6))//
                .shortName(Utils.uniqueRandomStringGenerator("", 4))//
                .description("Loan Product Description")//
                .includeInBorrowerCycle(false)//
                .currencyCode("USD")//
                .digitsAfterDecimal(2)//
                .inMultiplesOf(0)//
                .installmentAmountInMultiplesOf(1)//
                .useBorrowerCycle(false)//
                .minPrincipal(100.0)//
                .principal(1000.0)//
                .maxPrincipal(100000.0)//
                .minNumberOfRepayments(1)//
                .numberOfRepayments(1)//
                .maxNumberOfRepayments(30)//
                .isLinkedToFloatingInterestRates(false)//
                .minInterestRatePerPeriod((double) 0)//
                .interestRatePerPeriod((double) 0)//
                .maxInterestRatePerPeriod((double) 100)//
                .interestRateFrequencyType(2)//
                .repaymentEvery(30)//
                .repaymentFrequencyType(0L)//
                .amortizationType(1)//
                .interestType(0)//
                .isEqualAmortization(false)//
                .interestCalculationPeriodType(1)//
                .transactionProcessingStrategyCode(
                        LoanProductTestBuilder.DUE_PENALTY_FEE_INTEREST_PRINCIPAL_IN_ADVANCE_PRINCIPAL_PENALTY_FEE_INTEREST_STRATEGY)//
                .loanScheduleType(LoanScheduleType.CUMULATIVE.toString()) //
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
                .charges(Collections.emptyList())//
                .accountingRule(3)//
                .fundSourceAccountId(FUND_SOURCE.getAccountID().longValue())//
                .loanPortfolioAccountId(LOANS_RECEIVABLE_ACCOUNT.getAccountID().longValue())//
                .transfersInSuspenseAccountId(SUSPENSE_ACCOUNT.getAccountID().longValue())//
                .interestOnLoanAccountId(INTEREST_INCOME_ACCOUNT.getAccountID().longValue())//
                .incomeFromFeeAccountId(FEE_INCOME_ACCOUNT.getAccountID().longValue())//
                .incomeFromPenaltyAccountId(PENALTY_INCOME_ACCOUNT.getAccountID().longValue())//
                .incomeFromRecoveryAccountId(RECOVERIES_ACCOUNT.getAccountID().longValue())//
                .writeOffAccountId(WRITTEN_OFF_ACCOUNT.getAccountID().longValue())//
                .overpaymentLiabilityAccountId(OVERPAYMENT_ACCOUNT.getAccountID().longValue())//
                .receivableInterestAccountId(INTEREST_RECEIVABLE_ACCOUNT.getAccountID().longValue())//
                .receivableFeeAccountId(FEE_RECEIVABLE_ACCOUNT.getAccountID().longValue())//
                .receivablePenaltyAccountId(PENALTY_RECEIVABLE_ACCOUNT.getAccountID().longValue())//
                .goodwillCreditAccountId(GOODWILL_EXPENSE_ACCOUNT.getAccountID().longValue())//
                .incomeFromGoodwillCreditInterestAccountId(INTEREST_INCOME_CHARGE_OFF_ACCOUNT.getAccountID().longValue())//
                .incomeFromGoodwillCreditFeesAccountId(FEE_CHARGE_OFF_ACCOUNT.getAccountID().longValue())//
                .incomeFromGoodwillCreditPenaltyAccountId(FEE_CHARGE_OFF_ACCOUNT.getAccountID().longValue())//
                .incomeFromChargeOffInterestAccountId(INTEREST_INCOME_CHARGE_OFF_ACCOUNT.getAccountID().longValue())//
                .incomeFromChargeOffFeesAccountId(FEE_CHARGE_OFF_ACCOUNT.getAccountID().longValue())//
                .incomeFromChargeOffPenaltyAccountId(PENALTY_CHARGE_OFF_ACCOUNT.getAccountID().longValue())//
                .chargeOffExpenseAccountId(CHARGE_OFF_EXPENSE_ACCOUNT.getAccountID().longValue())//
                .chargeOffFraudExpenseAccountId(CHARGE_OFF_FRAUD_EXPENSE_ACCOUNT.getAccountID().longValue())//
                .dateFormat(DATE_PATTERN)//
                .locale("en_GB")//
                .disallowExpectedDisbursements(true)//
                .allowApprovedDisbursedAmountsOverApplied(true)//
                .overAppliedCalculationType("percentage")//
                .overAppliedNumber(50);
    }

    protected PostLoanProductsRequest createOnePeriod30DaysLongNoInterestPeriodicAccrualProductWithAdvancedPaymentAllocation() {
        String futureInstallmentAllocationRule = "NEXT_INSTALLMENT";
        AdvancedPaymentData defaultAllocation = createDefaultPaymentAllocation(futureInstallmentAllocationRule);

        return createOnePeriod30DaysLongNoInterestPeriodicAccrualProduct() //
                .transactionProcessingStrategyCode("advanced-payment-allocation-strategy")//
                .loanScheduleType(LoanScheduleType.PROGRESSIVE.toString()) //
                .loanScheduleProcessingType(LoanScheduleProcessingType.HORIZONTAL.toString()) //
                .addPaymentAllocationItem(defaultAllocation);
    }

    protected PostLoanProductsRequest create1InstallmentAmountInMultiplesOf4Period1MonthLongWithInterestAndAmortizationProduct(
            int interestType, int amortizationType) {
        return createOnePeriod30DaysLongNoInterestPeriodicAccrualProduct().multiDisburseLoan(false)//
                .disallowExpectedDisbursements(false)//
                .allowApprovedDisbursedAmountsOverApplied(false)//
                .overAppliedCalculationType(null)//
                .overAppliedNumber(null)//
                .principal(1250.0)//
                .numberOfRepayments(4)//
                .repaymentEvery(1)//
                .repaymentFrequencyType(RepaymentFrequencyType.MONTHS.longValue())//
                .interestType(interestType)//
                .amortizationType(amortizationType);
    }

    protected void verifyUndoLastDisbursalShallFail(Long loanId, String expectedError) {
        ResponseSpecification errorResponse = new ResponseSpecBuilder().expectStatusCode(403).build();
        LoanTransactionHelper validationErrorHelper = new LoanTransactionHelper(this.REQUEST_SPEC, errorResponse);
        CallFailedRuntimeException exception = assertThrows(CallFailedRuntimeException.class, () -> {
            validationErrorHelper.undoLastDisbursalLoan(loanId, new PostLoansLoanIdRequest());
        });
        assertTrue(exception.getMessage().contains(expectedError));
    }

    protected void verifyNoTransactions(Long loanId) {
        verifyTransactions(loanId, (Transaction[]) null);
    }

    protected void verifyTransactions(Long loanId, Transaction... transactions) {
        GetLoansLoanIdResponse loanDetails = LOAN_TRANSACTION_HELPER.getLoan(REQUEST_SPEC, RESPONSE_SPEC, loanId.intValue());
        if (transactions == null || transactions.length == 0) {
            assertNull(loanDetails.getTransactions(), "No transaction is expected");
        } else {
            Assertions.assertEquals(transactions.length, loanDetails.getTransactions().size());
            Arrays.stream(transactions).forEach(tr -> {
                Optional<GetLoansLoanIdTransactions> optTx = loanDetails.getTransactions().stream()
                        .filter(item -> Objects.equals(item.getAmount(), tr.amount) //
                                && Objects.equals(item.getType().getValue(), tr.type) //
                                && Objects.equals(item.getDate(), LocalDate.parse(tr.date, DATE_FORMATTER)))
                        .findFirst();
                Assertions.assertTrue(optTx.isPresent(), "Required transaction  not found: " + tr);

                GetLoansLoanIdTransactions tx = optTx.get();

                if (tr.reversed != null) {
                    Assertions.assertEquals(tr.reversed, tx.getManuallyReversed(), "Transaction is not reversed: " + tr);
                }
            });
        }
    }

    protected void verifyTransactions(Long loanId, TransactionExt... transactions) {
        GetLoansLoanIdResponse loanDetails = LOAN_TRANSACTION_HELPER.getLoan(REQUEST_SPEC, RESPONSE_SPEC, loanId.intValue());
        if (transactions == null || transactions.length == 0) {
            assertNull(loanDetails.getTransactions(), "No transaction is expected");
        } else {
            Assertions.assertEquals(transactions.length, loanDetails.getTransactions().size());
            Arrays.stream(transactions).forEach(tr -> {
                boolean found = loanDetails.getTransactions().stream().anyMatch(item -> Objects.equals(item.getAmount(), tr.amount) //
                        && Objects.equals(item.getType().getValue(), tr.type) //
                        && Objects.equals(item.getDate(), LocalDate.parse(tr.date, DATE_FORMATTER)) //
                        && Objects.equals(item.getOutstandingLoanBalance(), tr.outstandingPrincipal) //
                        && Objects.equals(item.getPrincipalPortion(), tr.principalPortion) //
                        && Objects.equals(item.getInterestPortion(), tr.interestPortion) //
                        && Objects.equals(item.getFeeChargesPortion(), tr.feePortion) //
                        && Objects.equals(item.getPenaltyChargesPortion(), tr.penaltyPortion) //
                        && Objects.equals(item.getOverpaymentPortion(), tr.overpaymentPortion) //
                        && Objects.equals(item.getUnrecognizedIncomePortion(), tr.unrecognizedPortion) //
                );
                Assertions.assertTrue(found, "Required transaction not found: " + tr);
            });
        }
    }

    protected void placeHardLockOnLoan(Long loanId) {
        LOAN_ACCOUNT_LOCK_HELPER.placeSoftLockOnLoanAccount(loanId.intValue(), "LOAN_COB_CHUNK_PROCESSING");
    }

    protected void executeInlineCOB(Long loanId) {
        INLINE_LOAN_COB_HELPER.executeInlineCOB(List.of(loanId));
    }

    protected void reAgeLoan(Long loanId, String frequencyType, int frequencyNumber, String startDate, Integer numberOfInstallments) {
        PostLoansLoanIdTransactionsRequest request = new PostLoansLoanIdTransactionsRequest();
        request.setDateFormat(DATE_PATTERN);
        request.setLocale("en");
        request.setFrequencyType(frequencyType);
        request.setFrequencyNumber(frequencyNumber);
        request.setStartDate(startDate);
        request.setNumberOfInstallments(numberOfInstallments);
        LOAN_TRANSACTION_HELPER.reAge(loanId, request);
    }

    protected void reAmortizeLoan(Long loanId) {
        PostLoansLoanIdTransactionsRequest request = new PostLoansLoanIdTransactionsRequest();
        request.setDateFormat(DATE_PATTERN);
        request.setLocale("en");
        LOAN_TRANSACTION_HELPER.reAmortize(loanId, request);
    }

    protected void undoReAgeLoan(Long loanId) {
        LOAN_TRANSACTION_HELPER.undoReAge(loanId, new PostLoansLoanIdTransactionsRequest());
    }

    protected void undoReAmortizeLoan(Long loanId) {
        LOAN_TRANSACTION_HELPER.undoReAmortize(loanId, new PostLoansLoanIdTransactionsRequest());
    }

    protected void verifyLastClosedBusinessDate(Long loanId, String lastClosedBusinessDate) {
        GetLoansLoanIdResponse loanDetails = LOAN_TRANSACTION_HELPER.getLoanDetails(loanId);
        assertNotNull(loanDetails.getLastClosedBusinessDate());
        Assertions.assertEquals(lastClosedBusinessDate, loanDetails.getLastClosedBusinessDate().format(DATE_FORMATTER));
    }

    protected void disburseLoan(Long loanId, BigDecimal amount, String date) {
        LOAN_TRANSACTION_HELPER.disburseLoan(loanId,
                new PostLoansLoanIdRequest().actualDisbursementDate(date).dateFormat(DATE_PATTERN).transactionAmount(amount).locale("en"));
    }

    protected void undoDisbursement(Integer loanId) {
        LOAN_TRANSACTION_HELPER.undoDisbursal(loanId);
    }

    protected void verifyJournalEntries(Long loanId, Journal... entries) {
        GetJournalEntriesTransactionIdResponse journalEntriesForLoan = JOURNAL_ENTRY_HELPER.getJournalEntriesForLoan(loanId);
        Assertions.assertEquals(entries.length, journalEntriesForLoan.getPageItems().size());
        Arrays.stream(entries).forEach(journalEntry -> {
            boolean found = journalEntriesForLoan.getPageItems().stream()
                    .anyMatch(item -> Objects.equals(item.getAmount(), journalEntry.amount)
                            && Objects.equals(item.getGlAccountId(), journalEntry.account.getAccountID().longValue())
                            && Objects.requireNonNull(item.getEntryType()).getValue().equals(journalEntry.type));
            Assertions.assertTrue(found, "Required journal entry not found: " + journalEntry);
        });
    }

    protected void verifyTRJournalEntries(Long transactionId, Journal... entries) {
        GetJournalEntriesTransactionIdResponse journalEntriesForLoan = JOURNAL_ENTRY_HELPER
                .getJournalEntries("L" + transactionId.toString());
        Assertions.assertEquals(entries.length, journalEntriesForLoan.getPageItems().size());
        Arrays.stream(entries).forEach(journalEntry -> {
            boolean found = journalEntriesForLoan.getPageItems().stream()
                    .anyMatch(item -> Objects.equals(item.getAmount(), journalEntry.amount)
                            && Objects.equals(item.getGlAccountId(), journalEntry.account.getAccountID().longValue())
                            && Objects.requireNonNull(item.getEntryType()).getValue().equals(journalEntry.type));
            Assertions.assertTrue(found, "Required journal entry not found: " + journalEntry);
        });
    }

    protected Long addCharge(Long loanId, boolean isPenalty, double amount, String dueDate) {
        Integer chargeId = ChargesHelper.createCharges(REQUEST_SPEC, RESPONSE_SPEC,
                ChargesHelper.getLoanSpecifiedDueDateJSON(ChargesHelper.CHARGE_CALCULATION_TYPE_FLAT, String.valueOf(amount), isPenalty));
        assertNotNull(chargeId);
        Integer loanChargeId = this.LOAN_TRANSACTION_HELPER.addChargesForLoan(loanId.intValue(),
                LoanTransactionHelper.getSpecifiedDueDateChargesForLoanAsJSON(String.valueOf(chargeId), dueDate, String.valueOf(amount)));
        assertNotNull(loanChargeId);
        return loanChargeId.longValue();
    }

    protected void verifyRepaymentSchedule(Long loanId, Installment... installments) {
        GetLoansLoanIdResponse loanResponse = LOAN_TRANSACTION_HELPER.getLoan(REQUEST_SPEC, RESPONSE_SPEC, loanId.intValue());
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(DATE_PATTERN);

        assertNotNull(loanResponse.getRepaymentSchedule());
        assertNotNull(loanResponse.getRepaymentSchedule().getPeriods());
        Assertions.assertEquals(installments.length, loanResponse.getRepaymentSchedule().getPeriods().size(),
                "Expected installments are not matching with the installments configured on the loan");

        int installmentNumber = 0;
        for (int i = 1; i < installments.length; i++) {
            GetLoansLoanIdRepaymentPeriod period = loanResponse.getRepaymentSchedule().getPeriods().get(i);
            Double principalDue = period.getPrincipalDue();
            Double amount = installments[i].principalAmount;

            if (installments[i].completed == null) { // this is for the disbursement
                Assertions.assertEquals(amount, period.getPrincipalLoanBalanceOutstanding(),
                        "%d. installment's principal due is different, expected: %.2f, actual: %.2f".formatted(i, amount,
                                period.getPrincipalLoanBalanceOutstanding()));
            } else {
                Assertions.assertEquals(amount, principalDue,
                        "%d. installment's principal due is different, expected: %.2f, actual: %.2f".formatted(i, amount, principalDue));

                Double interestAmount = installments[i].interestAmount;
                Double interestDue = period.getInterestDue();
                if (interestAmount != null) {
                    Assertions.assertEquals(interestAmount, interestDue,
                            "%d. installment's interest due is different, expected: %.2f, actual: %.2f".formatted(i, interestAmount,
                                    interestDue));
                }

                Double feeAmount = installments[i].feeAmount;
                Double feeDue = period.getFeeChargesDue();
                if (feeAmount != null) {
                    Assertions.assertEquals(feeAmount, feeDue,
                            "%d. installment's fee charges due is different, expected: %.2f, actual: %.2f".formatted(i, feeAmount, feeDue));
                }

                Double penaltyAmount = installments[i].penaltyAmount;
                Double penaltyDue = period.getPenaltyChargesDue();
                if (penaltyAmount != null) {
                    Assertions.assertEquals(penaltyAmount, penaltyDue,
                            "%d. installment's penalty charges due is different, expected: %.2f, actual: %.2f".formatted(i, penaltyAmount,
                                    penaltyDue));
                }

                Double outstandingAmount = installments[i].totalOutstandingAmount;
                Double totalOutstanding = period.getTotalOutstandingForPeriod();
                if (outstandingAmount != null) {
                    Assertions.assertEquals(outstandingAmount, totalOutstanding,
                            "%d. installment's total outstanding is different, expected: %.2f, actual: %.2f".formatted(i, outstandingAmount,
                                    totalOutstanding));
                }

                Double outstandingPrincipalExpected = installments[i].outstandingAmounts != null
                        ? installments[i].outstandingAmounts.principalOutstanding
                        : null;
                Double outstandingPrincipal = period.getPrincipalOutstanding();
                if (outstandingPrincipalExpected != null) {
                    Assertions.assertEquals(outstandingPrincipalExpected, outstandingPrincipal,
                            "%d. installment's outstanding principal is different, expected: %.2f, actual: %.2f".formatted(i,
                                    outstandingPrincipalExpected, outstandingPrincipal));
                }

                Double outstandingFeeExpected = installments[i].outstandingAmounts != null
                        ? installments[i].outstandingAmounts.feeOutstanding
                        : null;
                Double outstandingFee = period.getFeeChargesOutstanding();
                if (outstandingFeeExpected != null) {
                    Assertions.assertEquals(outstandingFeeExpected, outstandingFee,
                            "%d. installment's outstanding fee is different, expected: %.2f, actual: %.2f".formatted(i,
                                    outstandingFeeExpected, outstandingFee));
                }

                Double outstandingPenaltyExpected = installments[i].outstandingAmounts != null
                        ? installments[i].outstandingAmounts.penaltyOutstanding
                        : null;
                Double outstandingPenalty = period.getPenaltyChargesOutstanding();
                if (outstandingPenaltyExpected != null) {
                    Assertions.assertEquals(outstandingPenaltyExpected, outstandingPenalty,
                            "%d. installment's outstanding penalty is different, expected: %.2f, actual: %.2f".formatted(i,
                                    outstandingPenaltyExpected, outstandingPenalty));
                }

                Double outstandingTotalExpected = installments[i].outstandingAmounts != null
                        ? installments[i].outstandingAmounts.totalOutstanding
                        : null;
                Double outstandingTotal = period.getTotalOutstandingForPeriod();
                if (outstandingTotalExpected != null) {
                    Assertions.assertEquals(outstandingTotalExpected, outstandingTotal,
                            "%d. installment's total outstanding is different, expected: %.2f, actual: %.2f".formatted(i,
                                    outstandingTotalExpected, outstandingTotal));
                }

                Double loanBalanceExpected = installments[i].loanBalance;
                Double loanBalance = period.getPrincipalLoanBalanceOutstanding();
                if (loanBalanceExpected != null) {
                    Assertions.assertEquals(loanBalanceExpected, loanBalance,
                            "%d. installment's loan balance is different, expected: %.2f, actual: %.2f".formatted(i, loanBalanceExpected,
                                    loanBalance));
                }
                installmentNumber++;
                Assertions.assertEquals(installmentNumber, period.getPeriod());
            }
            Assertions.assertEquals(installments[i].completed, period.getComplete());
            Assertions.assertEquals(LocalDate.parse(installments[i].dueDate, dateTimeFormatter), period.getDueDate());
        }
    }

    protected void runAt(String date, Runnable runnable) {
        try {
            GlobalConfigurationHelper.updateEnabledFlagForGlobalConfiguration(REQUEST_SPEC, RESPONSE_SPEC, 42, true);
            GlobalConfigurationHelper.updateIsBusinessDateEnabled(REQUEST_SPEC, RESPONSE_SPEC, TRUE);
            BUSINESS_DATE_HELPER.updateBusinessDate(
                    new BusinessDateRequest().type(BUSINESS_DATE.getName()).date(date).dateFormat(DATE_PATTERN).locale("en"));
            runnable.run();
        } finally {
            GlobalConfigurationHelper.updateIsBusinessDateEnabled(REQUEST_SPEC, RESPONSE_SPEC, FALSE);
            GlobalConfigurationHelper.updateEnabledFlagForGlobalConfiguration(REQUEST_SPEC, RESPONSE_SPEC, 42, false);
        }
    }

    protected void runAsNonByPass(Runnable runnable) {
        RequestSpecificationImpl requestSpecImpl = (RequestSpecificationImpl) REQUEST_SPEC;
        try {
            requestSpecImpl.replaceHeader("Authorization", "Basic " + NON_BY_PASS_USER_AUTH_KEY);
            runnable.run();
        } finally {
            requestSpecImpl.replaceHeader("Authorization", "Basic " + FULL_ADMIN_AUTH_KEY);
        }
    }

    protected PostLoansRequest applyLoanRequest(Long clientId, Long loanProductId, String loanDisbursementDate, Double amount,
            int numberOfRepayments) {
        return applyLoanRequest(clientId, loanProductId, loanDisbursementDate, amount, numberOfRepayments, null);
    }

    protected PostLoansRequest applyLoanRequest(Long clientId, Long loanProductId, String loanDisbursementDate, Double amount,
            int numberOfRepayments, Consumer<PostLoansRequest> customizer) {

        PostLoansRequest postLoansRequest = new PostLoansRequest().clientId(clientId).productId(loanProductId)
                .expectedDisbursementDate(loanDisbursementDate).dateFormat(DATE_PATTERN)
                .transactionProcessingStrategyCode(DUE_PENALTY_INTEREST_PRINCIPAL_FEE_IN_ADVANCE_PENALTY_INTEREST_PRINCIPAL_FEE_STRATEGY)
                .locale("en").submittedOnDate(loanDisbursementDate).amortizationType(1).interestRatePerPeriod(BigDecimal.ZERO)
                .interestCalculationPeriodType(1).interestType(0).repaymentEvery(30).repaymentFrequencyType(0)
                .numberOfRepayments(numberOfRepayments).loanTermFrequency(numberOfRepayments * 30).loanTermFrequencyType(0)
                .maxOutstandingLoanBalance(BigDecimal.valueOf(amount)).principal(BigDecimal.valueOf(amount)).loanType("individual");
        if (customizer != null) {
            customizer.accept(postLoansRequest);
        }
        return postLoansRequest;
    }

    protected PostLoansLoanIdRequest approveLoanRequest(Double amount, String approvalDate) {
        return new PostLoansLoanIdRequest().approvedLoanAmount(BigDecimal.valueOf(amount)).dateFormat(DATE_PATTERN)
                .approvedOnDate(approvalDate).locale("en");
    }

    protected Long applyAndApproveLoan(Long clientId, Long loanProductId, String loanDisbursementDate, Double amount,
            int numberOfRepayments) {
        return applyAndApproveLoan(clientId, loanProductId, loanDisbursementDate, amount, numberOfRepayments, null);
    }

    protected Long applyAndApproveLoan(Long clientId, Long loanProductId, String loanDisbursementDate, Double amount,
            int numberOfRepayments, Consumer<PostLoansRequest> customizer) {
        PostLoansResponse postLoansResponse = LOAN_TRANSACTION_HELPER
                .applyLoan(applyLoanRequest(clientId, loanProductId, loanDisbursementDate, amount, numberOfRepayments, customizer));

        PostLoansLoanIdResponse approvedLoanResult = LOAN_TRANSACTION_HELPER.approveLoan(postLoansResponse.getResourceId(),
                approveLoanRequest(amount, loanDisbursementDate));

        return approvedLoanResult.getLoanId();
    }

    protected Long applyAndApproveLoan(Long clientId, Long loanProductId, String loanDisbursementDate, Double amount) {
        return applyAndApproveLoan(clientId, loanProductId, loanDisbursementDate, amount, 1);
    }

    protected Long addRepaymentForLoan(Long loanId, Double amount, String date) {
        String firstRepaymentUUID = UUID.randomUUID().toString();
        PostLoansLoanIdTransactionsResponse response = LOAN_TRANSACTION_HELPER.makeLoanRepayment(loanId,
                new PostLoansLoanIdTransactionsRequest().dateFormat(DATE_PATTERN).transactionDate(date).locale("en")
                        .transactionAmount(amount).externalId(firstRepaymentUUID));
        return response.getResourceId();
    }

    protected Long chargeOffLoan(Long loanId, String date) {
        String randomText = Utils.randomStringGenerator("en", 5) + Utils.randomNumberGenerator(6) + Utils.randomStringGenerator("is", 5);
        Integer chargeOffReasonId = CodeHelper.createChargeOffCodeValue(REQUEST_SPEC, RESPONSE_SPEC, randomText, 1);
        String transactionExternalId = UUID.randomUUID().toString();

        PostLoansLoanIdTransactionsResponse chargeOffTransaction = this.LOAN_TRANSACTION_HELPER.chargeOffLoan((long) loanId,
                new PostLoansLoanIdTransactionsRequest().transactionDate(date).locale("en").dateFormat("dd MMMM yyyy")
                        .externalId(transactionExternalId).chargeOffReasonId((long) chargeOffReasonId));
        return chargeOffTransaction.getResourceId();
    }

    protected void changeLoanFraudState(Long loanId, boolean fraudState) {
        String payload = LOAN_TRANSACTION_HELPER.getLoanFraudPayloadAsJSON("fraud", fraudState ? "true" : "false");
        PutLoansLoanIdResponse response = LOAN_TRANSACTION_HELPER.modifyLoanCommand(Math.toIntExact(loanId), "markAsFraud", payload,
                RESPONSE_SPEC);
        assertNotNull(response);
    }

    protected Long addChargebackForLoan(Long loanId, Long transactionId, Double amount) {
        PostLoansLoanIdTransactionsResponse response = LOAN_TRANSACTION_HELPER.chargebackLoanTransaction(loanId, transactionId,
                new PostLoansLoanIdTransactionsTransactionIdRequest().locale("en").transactionAmount(amount).paymentTypeId(1L));
        return response.getResourceId();
    }

    protected PostChargesResponse createCharge(Double amount) {
        String payload = ChargesHelper.getLoanSpecifiedDueDateJSON(ChargesHelper.CHARGE_CALCULATION_TYPE_FLAT, amount.toString(), false);
        return ChargesHelper.createLoanCharge(REQUEST_SPEC, RESPONSE_SPEC, payload);
    }

    protected PostLoansLoanIdChargesResponse addLoanCharge(Long loanId, Long chargeId, String date, Double amount) {
        String payload = LoanTransactionHelper.getSpecifiedDueDateChargesForLoanAsJSON(chargeId.toString(), date, amount.toString());
        return LOAN_TRANSACTION_HELPER.addChargeForLoan(loanId.intValue(), payload, RESPONSE_SPEC);
    }

    protected void waiveLoanCharge(Long loanId, Long chargeId, Integer installmentNumber) {
        String payload = LoanTransactionHelper.getWaiveChargeJSON(installmentNumber.toString());
        LOAN_TRANSACTION_HELPER.waiveChargesForLoan(loanId.intValue(), chargeId.intValue(), payload);
    }

    protected void updateBusinessDate(String date) {
        BUSINESS_DATE_HELPER.updateBusinessDate(
                new BusinessDateRequest().type(BUSINESS_DATE.getName()).date(date).dateFormat(DATE_PATTERN).locale("en"));
    }

    protected Long getTransactionId(Long loanId, String type, String date) {
        GetLoansLoanIdResponse loan = LOAN_TRANSACTION_HELPER.getLoan(REQUEST_SPEC, RESPONSE_SPEC, loanId.intValue());
        return loan.getTransactions().stream().filter(
                tr -> Objects.equals(tr.getType().getValue(), type) && Objects.equals(tr.getDate(), LocalDate.parse(date, DATE_FORMATTER)))
                .findAny().orElseThrow().getId();
    }

    protected Journal journalEntry(double amount, Account account, String type) {
        return new Journal(amount, account, type);
    }

    protected Journal debit(Account account, double amount) {
        return new Journal(amount, account, "DEBIT");
    }

    protected Journal credit(Account account, double amount) {
        return new Journal(amount, account, "CREDIT");
    }

    protected Transaction transaction(double principalAmount, String type, String date) {
        return new Transaction(principalAmount, type, date, null);
    }

    protected Transaction reversedTransaction(double principalAmount, String type, String date) {
        return new Transaction(principalAmount, type, date, true);
    }

    protected TransactionExt transaction(double amount, String type, String date, double outstandingPrincipal, double principalPortion,
            double interestPortion, double feePortion, double penaltyPortion, double unrecognizedIncomePortion, double overpaymentPortion) {
        return new TransactionExt(amount, type, date, outstandingPrincipal, principalPortion, interestPortion, feePortion, penaltyPortion,
                unrecognizedIncomePortion, overpaymentPortion, false);
    }

    protected TransactionExt transaction(double amount, String type, String date, double outstandingPrincipal, double principalPortion,
            double interestPortion, double feePortion, double penaltyPortion, double unrecognizedIncomePortion, double overpaymentPortion,
            boolean reversed) {
        return new TransactionExt(amount, type, date, outstandingPrincipal, principalPortion, interestPortion, feePortion, penaltyPortion,
                unrecognizedIncomePortion, overpaymentPortion, reversed);
    }

    protected Installment installment(double principalAmount, Boolean completed, String dueDate) {
        return new Installment(principalAmount, null, null, null, null, completed, dueDate, null, null);
    }

    protected Installment installment(double principalAmount, double interestAmount, double totalOutstandingAmount, Boolean completed,
            String dueDate) {
        return new Installment(principalAmount, interestAmount, null, null, totalOutstandingAmount, completed, dueDate, null, null);
    }

    protected Installment installment(double principalAmount, double interestAmount, double feeAmount, double totalOutstandingAmount,
            Boolean completed, String dueDate) {
        return new Installment(principalAmount, interestAmount, feeAmount, null, totalOutstandingAmount, completed, dueDate, null, null);
    }

    protected Installment installment(double principalAmount, double interestAmount, double feeAmount, double penaltyAmount,
            double totalOutstandingAmount, Boolean completed, String dueDate) {
        return new Installment(principalAmount, interestAmount, feeAmount, penaltyAmount, totalOutstandingAmount, completed, dueDate, null,
                null);
    }

    protected Installment installment(double principalAmount, double interestAmount, double feeAmount, double penaltyAmount,
            OutstandingAmounts outstandingAmounts, Boolean completed, String dueDate) {
        return new Installment(principalAmount, interestAmount, feeAmount, penaltyAmount, null, completed, dueDate, outstandingAmounts,
                null);
    }

    protected Installment installment(double principalAmount, double interestAmount, double feeAmount, double penaltyAmount,
            double totalOutstanding, Boolean completed, String dueDate, double loanBalance) {
        return new Installment(principalAmount, interestAmount, feeAmount, penaltyAmount, totalOutstanding, completed, dueDate, null,
                loanBalance);
    }

    protected OutstandingAmounts outstanding(double principal, double fee, double penalty, double total) {
        return new OutstandingAmounts(principal, fee, penalty, total);
    }

    protected BatchRequestBuilder batchRequest() {
        return new BatchRequestBuilder(REQUEST_SPEC, RESPONSE_SPEC);
    }

    protected void validateLoanSummaryBalances(GetLoansLoanIdResponse loanDetails, Double totalOutstanding, Double totalRepayment,
            Double principalOutstanding, Double principalPaid, Double totalOverpaid) {
        assertEquals(totalOutstanding, loanDetails.getSummary().getTotalOutstanding());
        assertEquals(totalRepayment, loanDetails.getSummary().getTotalRepayment());
        assertEquals(principalOutstanding, loanDetails.getSummary().getPrincipalOutstanding());
        assertEquals(principalPaid, loanDetails.getSummary().getPrincipalPaid());
        assertEquals(totalOverpaid, loanDetails.getTotalOverpaid());
    }

    protected void checkMaturityDates(long loanId, LocalDate expectedMaturityDate, LocalDate actualMaturityDate) {
        GetLoansLoanIdResponse loanDetails = LOAN_TRANSACTION_HELPER.getLoanDetails(loanId);

        assertEquals(expectedMaturityDate, loanDetails.getTimeline().getExpectedMaturityDate());
        assertEquals(actualMaturityDate, loanDetails.getTimeline().getActualMaturityDate());
    }

    protected void verifyLoanStatus(long loanId, LoanStatus loanStatus) {
        GetLoansLoanIdResponse loanDetails = LOAN_TRANSACTION_HELPER.getLoanDetails(loanId);

        assertEquals(loanStatus.getCode(), loanDetails.getStatus().getCode());
    }

    protected void updateBusinessDateAndExecuteCOBJob(String date) {
        BUSINESS_DATE_HELPER.updateBusinessDate(
                new BusinessDateRequest().type(BUSINESS_DATE.getName()).date(date).dateFormat(DATE_PATTERN).locale("en"));
        SCHEDULER_JOB_HELPER.executeAndAwaitJob("Loan COB");
    }

    @AllArgsConstructor
    protected static class DelinquencyData {

        Integer minAgeDays;
        Integer maxAgeDays;
        BigDecimal delinquentAmount;
    }

    protected static DelinquencyData delinquency(Integer minAgeDays, Integer maxAgeDays, String delinquentAmount) {
        return new DelinquencyData(minAgeDays, maxAgeDays, new BigDecimal(delinquentAmount));
    }

    protected void verifyDelinquency(Long loanId, Integer loanLevelDelinquentDays, String loanLevelDelinquentAmount,
            InstallmentLevelDelinquencyAPIIntegrationTests.DelinquencyData... expectedInstallmentLevelDelinquencyData) {
        GetLoansLoanIdResponse loan = LOAN_TRANSACTION_HELPER.getLoan(REQUEST_SPEC, RESPONSE_SPEC, loanId.intValue());
        assertThat(loan.getDelinquent()).isNotNull();
        List<GetLoansLoanIdLoanInstallmentLevelDelinquency> installmentLevelDelinquency = loan.getDelinquent()
                .getInstallmentLevelDelinquency();

        assertThat(loan.getDelinquent().getDelinquentDays()).isEqualTo(loanLevelDelinquentDays);
        assertThat(loan.getDelinquent().getDelinquentAmount()).isEqualByComparingTo(Double.valueOf(loanLevelDelinquentAmount));

        if (expectedInstallmentLevelDelinquencyData != null && expectedInstallmentLevelDelinquencyData.length > 0) {
            assertThat(installmentLevelDelinquency).isNotNull();
            assertThat(installmentLevelDelinquency).hasSize(expectedInstallmentLevelDelinquencyData.length);
            for (int i = 0; i < expectedInstallmentLevelDelinquencyData.length; i++) {
                assertThat(installmentLevelDelinquency.get(i).getMaximumAgeDays())
                        .isEqualTo(expectedInstallmentLevelDelinquencyData[i].maxAgeDays);
                assertThat(installmentLevelDelinquency.get(i).getMinimumAgeDays())
                        .isEqualTo(expectedInstallmentLevelDelinquencyData[i].minAgeDays);
                assertThat(installmentLevelDelinquency.get(i).getDelinquentAmount())
                        .isEqualByComparingTo(expectedInstallmentLevelDelinquencyData[i].delinquentAmount);
            }
        } else {
            assertThat(installmentLevelDelinquency).isNull();
        }
    }

    @RequiredArgsConstructor
    public static class BatchRequestBuilder {

        private final RequestSpecification requestSpec;
        private final ResponseSpecification responseSpec;
        private List<BatchRequest> requests = new ArrayList<>();

        public BatchRequestBuilder rescheduleLoan(Long requestId, Long loanId, String submittedOnDate, String rescheduleFromDate,
                String adjustedDueDate) {
            BatchRequest bRequest = new BatchRequest();
            bRequest.setRequestId(requestId);
            bRequest.setRelativeUrl("rescheduleloans");
            bRequest.setMethod("POST");

            bRequest.setBody("""
                        {
                            "loanId": %d,
                            "rescheduleFromDate": "%s",
                            "rescheduleReasonId": 1,
                            "submittedOnDate": "%s",
                            "rescheduleReasonComment": "",
                            "adjustedDueDate": "%s",
                            "graceOnPrincipal": "",
                            "graceOnInterest": "",
                            "extraTerms": "",
                            "newInterestRate": "",
                            "dateFormat": "%s",
                            "locale": "en"
                        }
                    """.formatted(loanId, rescheduleFromDate, submittedOnDate, adjustedDueDate, DATE_PATTERN));

            requests.add(bRequest);
            return this;
        }

        public BatchRequestBuilder approveRescheduleLoan(Long requestId, Long rescheduleBatchRequestId, String approvedOnDate) {
            BatchRequest bRequest = new BatchRequest();
            bRequest.setRequestId(requestId);
            bRequest.setRelativeUrl("rescheduleloans/$.resourceId?command=approve");
            bRequest.setMethod("POST");
            bRequest.setReference(rescheduleBatchRequestId);

            bRequest.setBody("""
                        {
                            "approvedOnDate": "%s",
                            "dateFormat": "%s",
                            "locale": "en"
                        }
                    """.formatted(approvedOnDate, DATE_PATTERN));

            requests.add(bRequest);
            return this;
        }

        public List<BatchResponse> executeEnclosingTransaction() {
            return BatchHelper.postBatchRequestsWithEnclosingTransaction(requestSpec, responseSpec, BatchHelper.toJsonString(requests));
        }

        public ErrorResponse executeEnclosingTransactionError(ResponseSpecification responseSpec) {
            return BatchHelper.postBatchRequestsWithoutEnclosingTransactionError(requestSpec, responseSpec,
                    BatchHelper.toJsonString(requests));
        }
    }

    @ToString
    @AllArgsConstructor
    public static class Transaction {

        Double amount;
        String type;
        String date;
        Boolean reversed;
    }

    @ToString
    @AllArgsConstructor
    public static class TransactionExt {

        Double amount;
        String type;
        String date;
        Double outstandingPrincipal;
        Double principalPortion;
        Double interestPortion;
        Double feePortion;
        Double penaltyPortion;
        Double unrecognizedPortion;
        Double overpaymentPortion;
        Boolean reversed;
    }

    @ToString
    @AllArgsConstructor
    public static class Journal {

        Double amount;
        Account account;
        String type;
    }

    @ToString
    @AllArgsConstructor
    public static class Installment {

        Double principalAmount;
        Double interestAmount;
        Double feeAmount;
        Double penaltyAmount;
        Double totalOutstandingAmount;
        Boolean completed;
        String dueDate;
        OutstandingAmounts outstandingAmounts;
        Double loanBalance;
    }

    @AllArgsConstructor
    @ToString
    public static class OutstandingAmounts {

        Double principalOutstanding;
        Double feeOutstanding;
        Double penaltyOutstanding;
        Double totalOutstanding;
    }

    public static class AmortizationType {

        public static final Integer EQUAL_INSTALLMENTS = 1;
    }

    public static class InterestType {

        public static final Integer DECLINING_BALANCE = 0;
        public static final Integer FLAT = 1;
    }

    public static class RepaymentFrequencyType {

        public static final Integer MONTHS = 2;
        public static final String MONTHS_STRING = "MONTHS";
        public static final Integer DAYS = 0;
        public static final String DAYS_STRING = "DAYS";
    }

    public static class InterestCalculationPeriodType {

        public static final Integer SAME_AS_REPAYMENT_PERIOD = 1;
    }

    public static class InterestRateFrequencyType {

        public static final Integer MONTHS = 2;
        public static final Integer YEARS = 3;
    }

    protected static Stream<Arguments> processingStrategy() {
        return Stream.of(Arguments.of(Named.of("originalStrategy", false)), //
                Arguments.of(Named.of("advancedStrategy", true)));
    }

    protected static Stream<Arguments> loanProductFactory() {
        return Stream.of(Arguments.of(Named.of("DEFAULT_STRATEGY", new LoanProductTestBuilder().withRepaymentStrategy(DEFAULT_STRATEGY))),
                Arguments.of(Named.of("ADVANCED_PAYMENT_ALLOCATION_STRATEGY",
                        new LoanProductTestBuilder().withRepaymentStrategy(ADVANCED_PAYMENT_ALLOCATION_STRATEGY)
                                .withLoanScheduleType(LoanScheduleType.PROGRESSIVE).addAdvancedPaymentAllocation(
                                        createDefaultPaymentAllocation(), createPaymentAllocation("REPAYMENT", "NEXT_INSTALLMENT")))));
    }
}
