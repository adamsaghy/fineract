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
package org.apache.fineract.portfolio.loanaccount.loanschedule.data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.fineract.infrastructure.core.service.MathUtil;
import org.apache.fineract.organisation.monetary.domain.Money;

@ToString
@EqualsAndHashCode(exclude = { "previous", "next" })
public class RepaymentPeriod {

    @ToString.Exclude
    private final RepaymentPeriod previous;
    @Getter
    private final LocalDate fromDate;
    @Getter
    private final LocalDate dueDate;
    @Getter
    private final List<InterestPeriod> interestPeriods;
    @Setter
    @ToString.Exclude
    private RepaymentPeriod next;
    @Setter
    @Getter
    private Money emi;
    @Getter
    private Money paidPrincipal;
    @Getter
    private Money paidInterest;

    public RepaymentPeriod(RepaymentPeriod previous, LocalDate fromDate, LocalDate dueDate, Money emi) {
        this.previous = previous;
        if (previous != null) {
            previous.setNext(this);
        }
        this.fromDate = fromDate;
        this.dueDate = dueDate;
        this.emi = emi;
        this.interestPeriods = new ArrayList<>();
        // There is always at least 1 interest period, by default with same from-due date as repayment period
        getInterestPeriods().add(new InterestPeriod(this, getFromDate(), getDueDate(), BigDecimal.ZERO, getZero(), getZero(), getZero()));
        this.paidInterest = getZero();
        this.paidPrincipal = getZero();
    }

    public RepaymentPeriod(RepaymentPeriod previous, RepaymentPeriod repaymentPeriod) {
        this.previous = previous;
        if (previous != null) {
            previous.setNext(this);
        }
        this.fromDate = repaymentPeriod.fromDate;
        this.dueDate = repaymentPeriod.dueDate;
        this.emi = repaymentPeriod.emi;
        this.interestPeriods = new ArrayList<>();
        this.paidPrincipal = repaymentPeriod.paidPrincipal;
        this.paidInterest = repaymentPeriod.paidInterest;
        // There is always at least 1 interest period, by default with same from-due date as repayment period
        for (InterestPeriod interestPeriod : repaymentPeriod.interestPeriods) {
            interestPeriods.add(new InterestPeriod(this, interestPeriod));
        }
    }

    public void addInterestPeriod(InterestPeriod interestPeriod) {
        interestPeriods.add(interestPeriod);
        Collections.sort(interestPeriods);
    }

    public Optional<RepaymentPeriod> getPrevious() {
        return Optional.ofNullable(previous);
    }

    public Optional<RepaymentPeriod> getNext() {
        return Optional.ofNullable(next);
    }

    public BigDecimal getRateFactorPlus1() {
        return interestPeriods.stream().map(InterestPeriod::getRateFactor).reduce(BigDecimal.ONE, BigDecimal::add);
    }

    public Money getCalculatedDueInterest() {
        Money calculatedDueInterest = getInterestPeriods().stream().map(InterestPeriod::getCalculatedDueInterest).reduce(getZero(),
                Money::plus);
        if (getPrevious().isPresent()) {
            calculatedDueInterest = calculatedDueInterest.add(getPrevious().get().getUnrecognizedInterest());
        }
        return calculatedDueInterest;
    }

    private Money getZero() {
        // EMI is always initiated
        return this.emi.zero();
    }

    public Money getCalculatedDuePrincipal() {
        return getEmi().minus(getCalculatedDueInterest());
    }

    public boolean isFullyPaid() {
        return getEmi().compareTo(getPaidPrincipal().plus(getPaidInterest())) == 0;
    }

    public Money getDueInterest() {
        // Due interest might be the maximum paid if there is pay-off or early repayment
        return MathUtil.max(getPaidPrincipal().compareTo(getCalculatedDuePrincipal()) > 0 ? getPaidInterest() : getCalculatedDueInterest(),
                getPaidInterest(), false);
    }

    public Money getDuePrincipal() {
        // Due principal might be the maximum paid if there is pay-off or early repayment
        return MathUtil.max(getEmi().minus(getDueInterest()), getPaidPrincipal(), false);
    }

    public Money getUnrecognizedInterest() {
        return getCalculatedDueInterest().minus(getDueInterest());
    }

    public boolean isLastPeriod() {
        return next == null;
    }

    public Money getOutstandingLoanBalance() {
        InterestPeriod lastInstallmentPeriod = getInterestPeriods().get(getInterestPeriods().size() - 1);
        Money calculatedOutStandingLoanBalance = lastInstallmentPeriod.getOutstandingLoanBalance() //
                .plus(lastInstallmentPeriod.getBalanceCorrectionAmount()) //
                .plus(lastInstallmentPeriod.getDisbursementAmount()) //
                .minus(getDuePrincipal())//
                .plus(getPaidPrincipal());//
        return MathUtil.negativeToZero(calculatedOutStandingLoanBalance);
    }

    public void addPaidPrincipalAmount(Money paidPrincipal) {
        this.paidPrincipal = MathUtil.plus(this.paidPrincipal, paidPrincipal);
    }

    public void addPaidInterestAmount(Money paidInterest) {
        this.paidInterest = MathUtil.plus(this.paidInterest, paidInterest);
    }

    public Money getInitialBalanceForEmiRecalculation() {
        Money initialBalance;
        if (getPrevious().isPresent()) {
            initialBalance = getPrevious().get().getOutstandingLoanBalance();
        } else {
            initialBalance = getZero();
        }
        Money totalDisbursedAmount = getInterestPeriods().stream().map(InterestPeriod::getDisbursementAmount).reduce(getZero(),
                Money::plus);
        return initialBalance.add(totalDisbursedAmount);
    }
}
