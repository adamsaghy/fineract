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
package org.apache.fineract.portfolio.loanaccount.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class LoanBalanceServiceTest {

    private static final MonetaryCurrency USD = new MonetaryCurrency("USD", 2, 0);
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 7, 9);
    private static final String TENANT_IDENTIFIER = "test";
    private final LoanBalanceService loanBalanceService = new LoanBalanceService(null, null, null);

    @BeforeEach
    public void setUp() {
        ThreadLocalContextUtil.setTenant(FineractPlatformTenant.builder().tenantIdentifier(TENANT_IDENTIFIER).timezoneId("UTC").build());
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, BUSINESS_DATE)));
        MoneyHelper.initializeTenantRoundingMode(TENANT_IDENTIFIER, RoundingMode.HALF_EVEN.ordinal());
    }

    @AfterEach
    public void tearDown() {
        MoneyHelper.clearCacheForTenant(TENANT_IDENTIFIER);
        ThreadLocalContextUtil.reset();
    }

    @Test
    public void updateLoanOutstandingBalancesRecalculatesOutstandingPrincipalInChronologicalOrder() {
        LoanTransaction disbursement = transaction(LoanTransactionType.DISBURSEMENT, "2026-01-10", "1000", null);
        LoanTransaction repayment = transaction(LoanTransactionType.REPAYMENT, "2026-02-10", "100", "100");
        LoanTransaction backdatedCapitalizedIncome = transaction(LoanTransactionType.CAPITALIZED_INCOME, "2026-02-01", "50", "50");
        LoanTransaction creditBalanceRefund = transaction(LoanTransactionType.CREDIT_BALANCE_REFUND, "2026-03-01", "400", "150");
        LoanTransaction chargeback = transaction(LoanTransactionType.CHARGEBACK, "2026-04-01", "700", "200");
        LoanTransaction incomePosting = transaction(LoanTransactionType.INCOME_POSTING, "2026-05-01", "25", null);
        LoanTransaction reversedRepayment = transaction(LoanTransactionType.REPAYMENT, "2026-01-15", "300", "300", true);
        reversedRepayment.updateOutstandingLoanBalance(new BigDecimal("777.00"));

        Loan loan = new TestLoan(List.of(disbursement, repayment, backdatedCapitalizedIncome, creditBalanceRefund, chargeback,
                incomePosting, reversedRepayment));

        loanBalanceService.updateLoanOutstandingBalances(loan);

        assertThat(disbursement.getOutstandingLoanBalance()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(backdatedCapitalizedIncome.getOutstandingLoanBalance()).isEqualByComparingTo(new BigDecimal("1050.00"));
        assertThat(repayment.getOutstandingLoanBalance()).isEqualByComparingTo(new BigDecimal("950.00"));
        assertThat(creditBalanceRefund.getOutstandingLoanBalance()).isEqualByComparingTo(new BigDecimal("1100.00"));
        assertThat(chargeback.getOutstandingLoanBalance()).isEqualByComparingTo(new BigDecimal("1300.00"));
        assertThat(incomePosting.getOutstandingLoanBalance()).isEqualByComparingTo(new BigDecimal("1300.00"));
        assertThat(reversedRepayment.getOutstandingLoanBalance()).isEqualByComparingTo(new BigDecimal("777.00"));
    }

    @Test
    public void updateLoanOutstandingBalancesDoesNotCarryNegativeBalanceForward() {
        LoanTransaction disbursement = transaction(LoanTransactionType.DISBURSEMENT, "2026-01-10", "100", null);
        LoanTransaction repayment = transaction(LoanTransactionType.REPAYMENT, "2026-02-10", "150", "150");
        LoanTransaction chargeback = transaction(LoanTransactionType.CHARGEBACK, "2026-03-10", "25", "25");

        Loan loan = new TestLoan(List.of(disbursement, repayment, chargeback));

        loanBalanceService.updateLoanOutstandingBalances(loan);

        assertThat(disbursement.getOutstandingLoanBalance()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(repayment.getOutstandingLoanBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(chargeback.getOutstandingLoanBalance()).isEqualByComparingTo(new BigDecimal("25.00"));
    }

    private LoanTransaction transaction(final LoanTransactionType type, final String date, final String amount,
            final String principalPortion) {
        return transaction(type, date, amount, principalPortion, false);
    }

    private LoanTransaction transaction(final LoanTransactionType type, final String date, final String amount,
            final String principalPortion, final boolean reversed) {
        BigDecimal principal = principalPortion == null ? null : new BigDecimal(principalPortion);
        return new LoanTransaction(null, null, type, LocalDate.parse(date), new BigDecimal(amount), principal, null, null, null, null,
                reversed, null, null);
    }

    private static final class TestLoan extends Loan {

        private final List<LoanTransaction> transactions;

        private TestLoan(final List<LoanTransaction> transactions) {
            this.transactions = transactions;
        }

        @Override
        public MonetaryCurrency getCurrency() {
            return USD;
        }

        @Override
        public List<LoanTransaction> getLoanTransactions() {
            return transactions;
        }
    }
}
