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

import java.math.BigDecimal;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.fineract.client.models.PostLoanProductsRequest;
import org.apache.fineract.client.models.PostLoanProductsResponse;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.loans.LoanTestLifecycleExtension;
import org.apache.fineract.integrationtests.common.products.DelinquencyBucketsHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@Slf4j
@ExtendWith(LoanTestLifecycleExtension.class)
public class InstallmentLevelDelinquencyAPIIntegrationTests extends BaseLoanIntegrationTest {

    @Test
    public void testInstallmentLevelDelinquencyFourRangesInTheBucket() {
        runAt("31 May 2023", () -> {
            // Create Client
            Long clientId = CLIENT_HELPER.createClient(ClientHelper.defaultClientCreationRequest()).getClientId();

            // Create DelinquencyBuckets
            Integer delinquencyBucketId = DelinquencyBucketsHelper.createDelinquencyBucket(REQUEST_SPEC, RESPONSE_SPEC, List.of(//
                    Pair.of(1, 10), //
                    Pair.of(11, 30), //
                    Pair.of(31, 60), //
                    Pair.of(61, null)//
            ));

            // Create Loan Product
            PostLoanProductsRequest loanProductsRequest = create1InstallmentAmountInMultiplesOf4Period1MonthLongWithInterestAndAmortizationProduct(
                    InterestType.FLAT, AmortizationType.EQUAL_INSTALLMENTS);
            loanProductsRequest.setEnableInstallmentLevelDelinquency(true);
            loanProductsRequest.setDelinquencyBucketId(delinquencyBucketId.longValue());
            PostLoanProductsResponse loanProductResponse = LOAN_PRODUCT_HELPER.createLoanProduct(loanProductsRequest);

            // Apply and Approve Loan
            Long loanId = applyAndApproveLoan(clientId, loanProductResponse.getResourceId(), "01 January 2023", 1250.0, 4);

            // Disburse Loan
            disburseLoan(loanId, BigDecimal.valueOf(1250), "01 January 2023");

            // Verify Repayment Schedule and Due Dates
            verifyRepaymentSchedule(loanId, //
                    installment(0, null, "01 January 2023"), //
                    installment(313.0, false, "31 January 2023"), // 120 days delinquent -> range4
                    installment(313.0, false, "02 March 2023"), // 90 days delinquent -> range4
                    installment(313.0, false, "01 April 2023"), // 60 days delinquent -> range3
                    installment(311.0, false, "01 May 2023") // 30 days delinquent -> range2
            );

            // since the current day is 31 May 2023, therefore all the installments are delinquent
            verifyDelinquency(loanId, 120, "1250.0", //
                    delinquency(11, 30, "311.0"), // 4th installment
                    delinquency(31, 60, "313.0"), // 3rd installment
                    delinquency(61, null, "626.0") // 1st installment + 2nd installment
            );

            // Repayment of the first two installments
            addRepaymentForLoan(loanId, 626.0, "31 May 2023");
            verifyDelinquency(loanId, 60, "624.0", //
                    delinquency(11, 30, "311.0"), // 4th installment
                    delinquency(31, 60, "313.0") // 3rd installment
            );

            // Partial repayment
            addRepaymentForLoan(loanId, 100.0, "31 May 2023");
            verifyDelinquency(loanId, 60, "524.0", //
                    delinquency(11, 30, "311.0"), // 4th installment
                    delinquency(31, 60, "213.0") // 3rd installment
            );

            // Repay the loan fully
            addRepaymentForLoan(loanId, 524.0, "31 May 2023");
            verifyDelinquency(loanId, 0, "0.0");
        });
    }

    @Test
    public void testInstallmentLevelDelinquencyTwoRangesInTheBucket() {
        runAt("31 May 2023", () -> {
            // Create Client
            Long clientId = CLIENT_HELPER.createClient(ClientHelper.defaultClientCreationRequest()).getClientId();

            // Create DelinquencyBuckets
            Integer delinquencyBucketId = DelinquencyBucketsHelper.createDelinquencyBucket(REQUEST_SPEC, RESPONSE_SPEC, List.of(//
                    Pair.of(1, 60), //
                    Pair.of(61, null)//
            ));

            // Create Loan Product
            PostLoanProductsRequest loanProductsRequest = create1InstallmentAmountInMultiplesOf4Period1MonthLongWithInterestAndAmortizationProduct(
                    InterestType.FLAT, AmortizationType.EQUAL_INSTALLMENTS);
            loanProductsRequest.setEnableInstallmentLevelDelinquency(true);
            loanProductsRequest.setDelinquencyBucketId(delinquencyBucketId.longValue());
            PostLoanProductsResponse loanProductResponse = LOAN_PRODUCT_HELPER.createLoanProduct(loanProductsRequest);

            // Apply and Approve Loan
            Long loanId = applyAndApproveLoan(clientId, loanProductResponse.getResourceId(), "01 January 2023", 1250.0, 4);

            // Disburse Loan
            disburseLoan(loanId, BigDecimal.valueOf(1250), "01 January 2023");

            // Verify Repayment Schedule and Due Dates
            verifyRepaymentSchedule(loanId, //
                    installment(0, null, "01 January 2023"), //
                    installment(313.0, false, "31 January 2023"), // 120 days delinquent -> range2
                    installment(313.0, false, "02 March 2023"), // 90 days delinquent -> range2
                    installment(313.0, false, "01 April 2023"), // 60 days delinquent -> range1
                    installment(311.0, false, "01 May 2023") // 30 days delinquent -> range1
            );

            verifyDelinquency(loanId, 120, "1250.0", //
                    delinquency(1, 60, "624.0"), // 4th installment
                    delinquency(61, null, "626.0") // 1st installment + 2nd installment
            );

            // repay the first installment
            addRepaymentForLoan(loanId, 313.0, "31 May 2023");

            verifyDelinquency(loanId, 90, "937.0", //
                    delinquency(1, 60, "624.0"), // 4th installment
                    delinquency(61, null, "313.0") // 1st installment + 2nd installment
            );

        });
    }

    @Test
    public void testInstallmentLevelDelinquencyIsTurnedOff() {
        runAt("31 May 2023", () -> {
            // Create Client
            Long clientId = CLIENT_HELPER.createClient(ClientHelper.defaultClientCreationRequest()).getClientId();

            // Create DelinquencyBuckets
            Integer delinquencyBucketId = DelinquencyBucketsHelper.createDelinquencyBucket(REQUEST_SPEC, RESPONSE_SPEC, List.of(//
                    Pair.of(1, 60), //
                    Pair.of(61, null)//
            ));

            // Create Loan Product
            PostLoanProductsRequest loanProductsRequest = create1InstallmentAmountInMultiplesOf4Period1MonthLongWithInterestAndAmortizationProduct(
                    InterestType.FLAT, AmortizationType.EQUAL_INSTALLMENTS);
            loanProductsRequest.setEnableInstallmentLevelDelinquency(false);
            loanProductsRequest.setDelinquencyBucketId(delinquencyBucketId.longValue());
            PostLoanProductsResponse loanProductResponse = LOAN_PRODUCT_HELPER.createLoanProduct(loanProductsRequest);

            // Apply and Approve Loan
            Long loanId = applyAndApproveLoan(clientId, loanProductResponse.getResourceId(), "01 January 2023", 1250.0, 4);

            // Disburse Loan
            disburseLoan(loanId, BigDecimal.valueOf(1250), "01 January 2023");

            // Verify Repayment Schedule and Due Dates
            verifyRepaymentSchedule(loanId, //
                    installment(0, null, "01 January 2023"), //
                    installment(313.0, false, "31 January 2023"), // 120 days delinquent -> range2
                    installment(313.0, false, "02 March 2023"), // 90 days delinquent -> range2
                    installment(313.0, false, "01 April 2023"), // 60 days delinquent -> range1
                    installment(311.0, false, "01 May 2023") // 30 days delinquent -> range1
            );

            // this should be empty as the installment level delinquency is not enabled for this loan
            verifyDelinquency(loanId, 120, "1250.0");
        });
    }

    @Test
    public void testInstallmentLevelDelinquencyUpdatedWhenCOBIsExecuted() {
        runAt("01 February 2023", () -> {
            // Create Client
            Long clientId = CLIENT_HELPER.createClient(ClientHelper.defaultClientCreationRequest()).getClientId();

            // Create DelinquencyBuckets
            Integer delinquencyBucketId = DelinquencyBucketsHelper.createDelinquencyBucket(REQUEST_SPEC, RESPONSE_SPEC, List.of(//
                    Pair.of(1, 1), //
                    Pair.of(2, null)//
            ));

            // Create Loan Product
            PostLoanProductsRequest loanProductsRequest = create1InstallmentAmountInMultiplesOf4Period1MonthLongWithInterestAndAmortizationProduct(
                    InterestType.FLAT, AmortizationType.EQUAL_INSTALLMENTS);
            loanProductsRequest.setEnableInstallmentLevelDelinquency(true);
            loanProductsRequest.setDelinquencyBucketId(delinquencyBucketId.longValue());
            PostLoanProductsResponse loanProductResponse = LOAN_PRODUCT_HELPER.createLoanProduct(loanProductsRequest);

            // Apply and Approve Loan
            Long loanId = applyAndApproveLoan(clientId, loanProductResponse.getResourceId(), "01 January 2023", 1250.0, 4);

            // Disburse Loan
            disburseLoan(loanId, BigDecimal.valueOf(1250), "01 January 2023");

            // Verify Repayment Schedule and Due Dates
            verifyRepaymentSchedule(loanId, //
                    installment(0, null, "01 January 2023"), installment(313.0, false, "31 January 2023"),
                    installment(313.0, false, "02 March 2023"), installment(313.0, false, "01 April 2023"),
                    installment(311.0, false, "01 May 2023"));

            // The first installment falls into the first range
            verifyDelinquency(loanId, 1, "313.0", //
                    delinquency(1, 1, "313.0") // 4th installment
            );

            // Let's go one day ahead in the time
            updateBusinessDateAndExecuteCOBJob("2 February 2023");

            // The first installment is not two days delinquent and therefore falls into the second range
            verifyDelinquency(loanId, 2, "313.0", //
                    delinquency(2, null, "313.0") // 4th installment
            );
        });
    }
}
