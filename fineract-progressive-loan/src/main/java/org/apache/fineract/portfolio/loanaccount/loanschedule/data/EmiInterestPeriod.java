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

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.apache.fineract.organisation.monetary.domain.Money;

@AllArgsConstructor
@EqualsAndHashCode(exclude = { "repaymentPeriod" })
@Data
public class EmiInterestPeriod implements Comparable<EmiInterestPeriod> {

    @ToString.Exclude
    private EmiRepaymentPeriod repaymentPeriod;

    private LocalDate fromDate;
    private LocalDate dueDate;

    private BigDecimal rateFactorMinus1;

    private Money disbursedAmount;
    private Money correctionAmount;
    private Money interestDue;

    public EmiInterestPeriod(final EmiInterestPeriod period, final EmiRepaymentPeriod repaymentPeriod) {
        this(repaymentPeriod, period.fromDate, period.dueDate, period.rateFactorMinus1, period.disbursedAmount, period.correctionAmount,
                period.interestDue);
    }

    @Override
    public int compareTo(@NotNull EmiInterestPeriod o) {
        return dueDate.compareTo(o.dueDate);
    }

    @ToString.Include(name = "repaymentPeriodDueDate")
    public LocalDate getRepaymentPeriodDueDate() {
        return repaymentPeriod != null ? repaymentPeriod.getDueDate() : null;
    }

    public void addDisbursedAmount(final Money outstandingBalance) {
        if (outstandingBalance != null && !outstandingBalance.isZero()) {
            this.disbursedAmount = this.disbursedAmount.add(outstandingBalance);
        }
    }

    public void addCorrectionAmount(final Money correctionAmount) {
        if (correctionAmount != null && !correctionAmount.isZero()) {
            this.correctionAmount = this.correctionAmount.add(correctionAmount);
        }
    }

    public boolean isAssignedOwnRepaymentPeriod() {
        return repaymentPeriod != null && !this.getFromDate().isBefore(repaymentPeriod.getFromDate())
                && (repaymentPeriod.isLastPeriod() || this.getFromDate().isBefore(repaymentPeriod.getDueDate()));
    }
}
