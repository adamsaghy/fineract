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

import static org.apache.fineract.portfolio.delinquency.domain.DelinquencyAction.PAUSE;
import static org.apache.fineract.portfolio.delinquency.domain.DelinquencyAction.RESUME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.specification.ResponseSpecification;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.fineract.client.models.BusinessDateResponse;
import org.apache.fineract.client.models.DeleteDelinquencyBucketResponse;
import org.apache.fineract.client.models.DeleteDelinquencyRangeResponse;
import org.apache.fineract.client.models.GetDelinquencyBucketsResponse;
import org.apache.fineract.client.models.GetDelinquencyRangesResponse;
import org.apache.fineract.client.models.GetDelinquencyTagHistoryResponse;
import org.apache.fineract.client.models.GetLoanProductsProductIdResponse;
import org.apache.fineract.client.models.GetLoansLoanIdDelinquencySummary;
import org.apache.fineract.client.models.GetLoansLoanIdLoanInstallmentLevelDelinquency;
import org.apache.fineract.client.models.GetLoansLoanIdRepaymentPeriod;
import org.apache.fineract.client.models.GetLoansLoanIdRepaymentSchedule;
import org.apache.fineract.client.models.GetLoansLoanIdResponse;
import org.apache.fineract.client.models.PostDelinquencyBucketResponse;
import org.apache.fineract.client.models.PostDelinquencyRangeResponse;
import org.apache.fineract.client.models.PostLoansDelinquencyActionResponse;
import org.apache.fineract.client.models.PostLoansLoanIdTransactionsResponse;
import org.apache.fineract.client.models.PutDelinquencyBucketResponse;
import org.apache.fineract.client.models.PutDelinquencyRangeResponse;
import org.apache.fineract.client.models.PutLoanProductsProductIdRequest;
import org.apache.fineract.client.models.PutLoanProductsProductIdResponse;
import org.apache.fineract.cob.data.JobBusinessStepConfigData;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.integrationtests.common.BusinessDateHelper;
import org.apache.fineract.integrationtests.common.BusinessStepConfigurationHelper;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.CommonConstants;
import org.apache.fineract.integrationtests.common.GlobalConfigurationHelper;
import org.apache.fineract.integrationtests.common.SchedulerJobHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.charges.ChargesHelper;
import org.apache.fineract.integrationtests.common.loans.CobHelper;
import org.apache.fineract.integrationtests.common.loans.LoanApplicationTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanProductTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanTestLifecycleExtension;
import org.apache.fineract.integrationtests.common.loans.LoanTransactionHelper;
import org.apache.fineract.integrationtests.common.products.DelinquencyBucketsHelper;
import org.apache.fineract.integrationtests.common.products.DelinquencyRangesHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@Slf4j
@ExtendWith(LoanTestLifecycleExtension.class)
public class DelinquencyBucketsIntegrationTest extends BaseLoanIntegrationTest {

    private static final String principalAmount = "10000";

    @Test
    public void testCreateDelinquencyRanges() {
        // given
        final String jsonRange = DelinquencyRangesHelper.getAsJSON(1, 3);

        // when
        final PostDelinquencyRangeResponse delinquencyRangeResponse01 = DelinquencyRangesHelper.createDelinquencyRange(REQUEST_SPEC,
                RESPONSE_SPEC, jsonRange);
        final ArrayList<GetDelinquencyRangesResponse> ranges = DelinquencyRangesHelper.getDelinquencyRanges(REQUEST_SPEC, RESPONSE_SPEC);

        // then
        assertNotNull(delinquencyRangeResponse01);
        assertNotNull(ranges);
        assertFalse(ranges.isEmpty());
        GetDelinquencyRangesResponse range = ranges.get(ranges.size() - 1);
        assertEquals(1, range.getMinimumAgeDays(), "Expected Min Age Days to 1");
        assertEquals(3, range.getMaximumAgeDays(), "Expected Max Age Days to 3");
    }

    @Test
    public void testUpdateDelinquencyRanges() {
        // given
        String jsonRange = DelinquencyRangesHelper.getAsJSON(1, 3);
        final PostDelinquencyRangeResponse delinquencyRangeResponse01 = DelinquencyRangesHelper.createDelinquencyRange(REQUEST_SPEC,
                RESPONSE_SPEC, jsonRange);
        jsonRange = DelinquencyRangesHelper.getAsJSON(1, 7);
        assertNotNull(delinquencyRangeResponse01);

        // when
        final PutDelinquencyRangeResponse delinquencyRangeResponse02 = DelinquencyRangesHelper.updateDelinquencyRange(REQUEST_SPEC,
                RESPONSE_SPEC, delinquencyRangeResponse01.getResourceId(), jsonRange);
        final GetDelinquencyRangesResponse range = DelinquencyRangesHelper.getDelinquencyRange(REQUEST_SPEC, RESPONSE_SPEC,
                delinquencyRangeResponse01.getResourceId());
        final DeleteDelinquencyRangeResponse deleteDelinquencyRangeResponse = DelinquencyRangesHelper.deleteDelinquencyRange(REQUEST_SPEC,
                RESPONSE_SPEC, delinquencyRangeResponse01.getResourceId());

        // then
        assertNotNull(delinquencyRangeResponse02);
        assertNotNull(deleteDelinquencyRangeResponse);
        assertNotNull(range);
        assertNotEquals(3, range.getMaximumAgeDays());
        assertEquals(1, range.getMinimumAgeDays());
        assertEquals(7, range.getMaximumAgeDays());
    }

    @Test
    public void testDelinquencyBuckets() {
        // given
        ArrayList<Integer> rangeIds = new ArrayList<>();
        String jsonRange = DelinquencyRangesHelper.getAsJSON(1, 3);
        PostDelinquencyRangeResponse delinquencyRangeResponse = DelinquencyRangesHelper.createDelinquencyRange(REQUEST_SPEC, RESPONSE_SPEC,
                jsonRange);
        rangeIds.add(delinquencyRangeResponse.getResourceId());
        jsonRange = DelinquencyRangesHelper.getAsJSON(4, 30);
        // Create
        delinquencyRangeResponse = DelinquencyRangesHelper.createDelinquencyRange(REQUEST_SPEC, RESPONSE_SPEC, jsonRange);
        rangeIds.add(delinquencyRangeResponse.getResourceId());
        String jsonBucket = DelinquencyBucketsHelper.getAsJSON(rangeIds);
        PostDelinquencyBucketResponse delinquencyBucketResponse = DelinquencyBucketsHelper.createDelinquencyBucket(REQUEST_SPEC,
                RESPONSE_SPEC, jsonBucket);
        // Update
        jsonRange = DelinquencyRangesHelper.getAsJSON(31, 60);
        rangeIds.add(delinquencyRangeResponse.getResourceId());
        jsonBucket = DelinquencyBucketsHelper.getAsJSON(rangeIds);
        PutDelinquencyBucketResponse updateDelinquencyBucketResponse = DelinquencyBucketsHelper.updateDelinquencyBucket(REQUEST_SPEC,
                RESPONSE_SPEC, delinquencyBucketResponse.getResourceId(), jsonBucket);
        delinquencyRangeResponse = DelinquencyRangesHelper.createDelinquencyRange(REQUEST_SPEC, RESPONSE_SPEC, jsonRange);
        rangeIds.add(delinquencyRangeResponse.getResourceId());
        // Read
        final GetDelinquencyBucketsResponse delinquencyBucket = DelinquencyBucketsHelper.getDelinquencyBucket(REQUEST_SPEC, RESPONSE_SPEC,
                delinquencyBucketResponse.getResourceId());

        // when
        final ArrayList<GetDelinquencyBucketsResponse> bucketList = DelinquencyBucketsHelper.getDelinquencyBuckets(REQUEST_SPEC,
                RESPONSE_SPEC);

        // then
        assertNotNull(bucketList);
        assertNotNull(delinquencyBucket);
        assertEquals(2, delinquencyBucket.getRanges().size());
        assertNotNull(delinquencyBucketResponse);
        assertNotNull(updateDelinquencyBucketResponse);
    }

    @Test
    public void testDelinquencyBucketDelete() {
        // given
        ArrayList<Integer> rangeIds = new ArrayList<>();
        String jsonRange = DelinquencyRangesHelper.getAsJSON(1, 3);
        PostDelinquencyRangeResponse delinquencyRangeResponse = DelinquencyRangesHelper.createDelinquencyRange(REQUEST_SPEC, RESPONSE_SPEC,
                jsonRange);
        rangeIds.add(delinquencyRangeResponse.getResourceId());
        jsonRange = DelinquencyRangesHelper.getAsJSON(4, 30);
        // Create
        delinquencyRangeResponse = DelinquencyRangesHelper.createDelinquencyRange(REQUEST_SPEC, RESPONSE_SPEC, jsonRange);
        rangeIds.add(delinquencyRangeResponse.getResourceId());
        String jsonBucket = DelinquencyBucketsHelper.getAsJSON(rangeIds);
        PostDelinquencyBucketResponse delinquencyBucketResponse = DelinquencyBucketsHelper.createDelinquencyBucket(REQUEST_SPEC,
                RESPONSE_SPEC, jsonBucket);
        // Delete
        DeleteDelinquencyBucketResponse deleteDelinquencyBucketResponse = DelinquencyBucketsHelper.deleteDelinquencyBucket(REQUEST_SPEC,
                RESPONSE_SPEC, delinquencyBucketResponse.getResourceId());

        // when
        final ArrayList<GetDelinquencyBucketsResponse> bucketList = DelinquencyBucketsHelper.getDelinquencyBuckets(REQUEST_SPEC,
                RESPONSE_SPEC);

        // then
        assertNotNull(bucketList);
        assertNotNull(delinquencyBucketResponse);
        assertNotNull(deleteDelinquencyBucketResponse);
    }

    @Test
    public void testDelinquencyBucketsRangeAgeOverlaped() {
        // Given
        ArrayList<Integer> rangeIds = new ArrayList<>();
        String jsonRange = DelinquencyRangesHelper.getAsJSON(1, 3);
        PostDelinquencyRangeResponse delinquencyRangeResponse = DelinquencyRangesHelper.createDelinquencyRange(REQUEST_SPEC, RESPONSE_SPEC,
                jsonRange);
        rangeIds.add(delinquencyRangeResponse.getResourceId());
        jsonRange = DelinquencyRangesHelper.getAsJSON(3, 30);
        // Create
        delinquencyRangeResponse = DelinquencyRangesHelper.createDelinquencyRange(REQUEST_SPEC, RESPONSE_SPEC, jsonRange);
        rangeIds.add(delinquencyRangeResponse.getResourceId());
        String jsonBucket = DelinquencyBucketsHelper.getAsJSON(rangeIds);
        final ResponseSpecification response403Spec = new ResponseSpecBuilder().expectStatusCode(403).build();

        // When
        DelinquencyBucketsHelper.createDelinquencyBucket(REQUEST_SPEC, response403Spec, jsonBucket);
    }

    @Test
    public void testDelinquencyBucketsNameDuplication() {
        // Given
        ArrayList<Integer> rangeIds = new ArrayList<>();
        String jsonRange = DelinquencyRangesHelper.getAsJSON(1, 3);
        PostDelinquencyRangeResponse delinquencyRangeResponse = DelinquencyRangesHelper.createDelinquencyRange(REQUEST_SPEC, RESPONSE_SPEC,
                jsonRange);
        rangeIds.add(delinquencyRangeResponse.getResourceId());
        jsonRange = DelinquencyRangesHelper.getAsJSON(4, 30);
        // Create
        delinquencyRangeResponse = DelinquencyRangesHelper.createDelinquencyRange(REQUEST_SPEC, RESPONSE_SPEC, jsonRange);
        rangeIds.add(delinquencyRangeResponse.getResourceId());
        String jsonBucket = DelinquencyBucketsHelper.getAsJSON(rangeIds);
        final ResponseSpecification response403Spec = new ResponseSpecBuilder().expectStatusCode(403).build();

        // When
        DelinquencyBucketsHelper.createDelinquencyBucket(REQUEST_SPEC, RESPONSE_SPEC, jsonBucket);

        // Then
        DelinquencyBucketsHelper.createDelinquencyBucket(REQUEST_SPEC, response403Spec, jsonBucket);
    }

    @Test
    public void testLoanProductCreationWithAndWithoutDelinquencyBucket() {
        // Given
        final LoanTransactionHelper loanTransactionHelper = new LoanTransactionHelper(this.REQUEST_SPEC, this.RESPONSE_SPEC);

        ArrayList<Integer> rangeIds = new ArrayList<>();
        // First Range
        String jsonRange = DelinquencyRangesHelper.getAsJSON(1, 3);
        PostDelinquencyRangeResponse delinquencyRangeResponse = DelinquencyRangesHelper.createDelinquencyRange(REQUEST_SPEC, RESPONSE_SPEC,
                jsonRange);
        rangeIds.add(delinquencyRangeResponse.getResourceId());
        jsonRange = DelinquencyRangesHelper.getAsJSON(4, 60);

        GetDelinquencyRangesResponse range = DelinquencyRangesHelper.getDelinquencyRange(REQUEST_SPEC, RESPONSE_SPEC,
                delinquencyRangeResponse.getResourceId());

        // Second Range
        delinquencyRangeResponse = DelinquencyRangesHelper.createDelinquencyRange(REQUEST_SPEC, RESPONSE_SPEC, jsonRange);
        rangeIds.add(delinquencyRangeResponse.getResourceId());

        range = DelinquencyRangesHelper.getDelinquencyRange(REQUEST_SPEC, RESPONSE_SPEC, delinquencyRangeResponse.getResourceId());
        final String classificationExpected = range.getClassification();
        log.info("Expected Delinquency Range classification after Disbursement {}", classificationExpected);

        String jsonBucket = DelinquencyBucketsHelper.getAsJSON(rangeIds);
        PostDelinquencyBucketResponse delinquencyBucketResponse = DelinquencyBucketsHelper.createDelinquencyBucket(REQUEST_SPEC,
                RESPONSE_SPEC, jsonBucket);
        assertNotNull(delinquencyBucketResponse);
        final GetDelinquencyBucketsResponse delinquencyBucket = DelinquencyBucketsHelper.getDelinquencyBucket(REQUEST_SPEC, RESPONSE_SPEC,
                delinquencyBucketResponse.getResourceId());

        // Loan product creation without Delinquency bucket
        GetLoanProductsProductIdResponse getLoanProductResponse = createLoanProduct(loanTransactionHelper, null, null);
        assertNotNull(getLoanProductResponse);
        assertNull(getLoanProductResponse.getDelinquencyBucket().getId());

        // Loan product creation with Delinquency bucket
        getLoanProductResponse = createLoanProduct(loanTransactionHelper, Math.toIntExact(delinquencyBucket.getId()), null);
        assertNotNull(getLoanProductResponse);
        log.info("Loan Product Bucket Name: {}", getLoanProductResponse.getDelinquencyBucket().getName());
        assertEquals(getLoanProductResponse.getDelinquencyBucket().getName(), delinquencyBucket.getName());

        // Update Loan product to remove the Delinquency bucket
        final Long loanProductId = getLoanProductResponse.getId();
        loanTransactionHelper.updateLoanProduct(loanProductId, "{delinquencyBucketId: null}");
        getLoanProductResponse = loanTransactionHelper.getLoanProduct(loanProductId.intValue());
        assertNotNull(getLoanProductResponse);
        assertNull(getLoanProductResponse.getDelinquencyBucket().getId());
    }

    @Test
    public void testLoanClassificationRealtime() {
        try {
            // Given
            final LoanTransactionHelper loanTransactionHelper = new LoanTransactionHelper(this.REQUEST_SPEC, this.RESPONSE_SPEC);
            GlobalConfigurationHelper.updateIsBusinessDateEnabled(REQUEST_SPEC, RESPONSE_SPEC, Boolean.TRUE);

            final LocalDate bussinesLocalDate = Utils.getDateAsLocalDate("01 March 2012");
            log.info("Current date {}", bussinesLocalDate);
            BusinessDateHelper.updateBusinessDate(REQUEST_SPEC, RESPONSE_SPEC, BusinessDateType.BUSINESS_DATE, bussinesLocalDate);
            final BusinessDateResponse businessDateResponse = BUSINESS_DATE_HELPER.getBusinessDateByType(REQUEST_SPEC, RESPONSE_SPEC,
                    BusinessDateType.BUSINESS_DATE);

            ArrayList<Integer> rangeIds = new ArrayList<>();
            // First Range
            String jsonRange = DelinquencyRangesHelper.getAsJSON(1, 3);
            PostDelinquencyRangeResponse delinquencyRangeResponse = DelinquencyRangesHelper.createDelinquencyRange(REQUEST_SPEC,
                    RESPONSE_SPEC, jsonRange);
            rangeIds.add(delinquencyRangeResponse.getResourceId());
            jsonRange = DelinquencyRangesHelper.getAsJSON(4, 60);

            GetDelinquencyRangesResponse range = DelinquencyRangesHelper.getDelinquencyRange(REQUEST_SPEC, RESPONSE_SPEC,
                    delinquencyRangeResponse.getResourceId());

            // Second Range
            delinquencyRangeResponse = DelinquencyRangesHelper.createDelinquencyRange(REQUEST_SPEC, RESPONSE_SPEC, jsonRange);
            rangeIds.add(delinquencyRangeResponse.getResourceId());

            range = DelinquencyRangesHelper.getDelinquencyRange(REQUEST_SPEC, RESPONSE_SPEC, delinquencyRangeResponse.getResourceId());
            final String classificationExpected = range.getClassification();
            log.info("Expected Delinquency Range classification after Disbursement {}", classificationExpected);

            String jsonBucket = DelinquencyBucketsHelper.getAsJSON(rangeIds);
            PostDelinquencyBucketResponse delinquencyBucketResponse = DelinquencyBucketsHelper.createDelinquencyBucket(REQUEST_SPEC,
                    RESPONSE_SPEC, jsonBucket);
            assertNotNull(delinquencyBucketResponse);
            final GetDelinquencyBucketsResponse delinquencyBucket = DelinquencyBucketsHelper.getDelinquencyBucket(REQUEST_SPEC,
                    RESPONSE_SPEC, delinquencyBucketResponse.getResourceId());

            // Client and Loan account creation
            final Integer clientId = ClientHelper.createClient(this.REQUEST_SPEC, this.RESPONSE_SPEC, "01 January 2012");
            final GetLoanProductsProductIdResponse getLoanProductsProductResponse = createLoanProduct(loanTransactionHelper,
                    Math.toIntExact(delinquencyBucket.getId()), null);
            assertNotNull(getLoanProductsProductResponse);
            log.info("Loan Product Bucket Name: {}", getLoanProductsProductResponse.getDelinquencyBucket().getName());
            assertEquals(getLoanProductsProductResponse.getDelinquencyBucket().getName(), delinquencyBucket.getName());

            // Older date to have more than one overdue installment
            final LocalDate transactionDate = bussinesLocalDate.minusDays(50);
            String operationDate = Utils.dateFormatter.format(transactionDate);

            // Create Loan Account
            final Integer loanId = createLoanAccount(loanTransactionHelper, clientId.toString(),
                    getLoanProductsProductResponse.getId().toString(), operationDate, null);

            GetLoansLoanIdResponse getLoansLoanIdResponse = loanTransactionHelper.getLoan(REQUEST_SPEC, RESPONSE_SPEC, loanId);
            assertNotNull(getLoansLoanIdResponse);
            assertNotNull(getLoansLoanIdResponse.getDelinquencyRange());
            log.info("Loan Delinquency Range after Disbursement {}", getLoansLoanIdResponse.getDelinquencyRange().getClassification());
            // First Loan Delinquency Classification after Disbursement command
            assertEquals(getLoansLoanIdResponse.getDelinquencyRange().getClassification(), classificationExpected);

            loanTransactionHelper.printRepaymentSchedule(getLoansLoanIdResponse);

            // Apply a partial repayment
            operationDate = Utils.dateFormatter.format(bussinesLocalDate);
            loanTransactionHelper.makeLoanRepayment(operationDate, 100.0f, loanId);

            getLoansLoanIdResponse = loanTransactionHelper.getLoan(REQUEST_SPEC, RESPONSE_SPEC, loanId);
            log.info("Loan Delinquency Range after Repayment {}", getLoansLoanIdResponse.getDelinquencyRange());
            assertNotNull(getLoansLoanIdResponse.getDelinquencyRange());
            // First Loan Delinquency Classification remains after Repayment because the installment is not fully paid
            assertEquals(getLoansLoanIdResponse.getDelinquencyRange().getClassification(), classificationExpected);

            // Apply a repayment to get a full paid installment
            loanTransactionHelper.makeLoanRepayment(operationDate, 1000.0f, loanId);
            getLoansLoanIdResponse = loanTransactionHelper.getLoan(REQUEST_SPEC, RESPONSE_SPEC, loanId);
            log.info("Loan Delinquency Range after Repayment {}", getLoansLoanIdResponse.getDelinquencyRange());
            assertNotNull(getLoansLoanIdResponse);
            // The Loan Delinquency Classification after Repayment command must be null
            assertNull(getLoansLoanIdResponse.getDelinquencyRange());
            // Get the Delinquency Tags
            ArrayList<GetDelinquencyTagHistoryResponse> getDelinquencyTagsHistory = loanTransactionHelper
                    .getLoanDelinquencyTags(REQUEST_SPEC, RESPONSE_SPEC, loanId);
            assertNotNull(getDelinquencyTagsHistory);
            log.info("Delinquency Tag History items {}", getDelinquencyTagsHistory.size());
            assertEquals(1, getDelinquencyTagsHistory.size());
            assertNotNull(getDelinquencyTagsHistory.get(0).getLiftedOnDate());
            assertEquals(getDelinquencyTagsHistory.get(0).getAddedOnDate(), businessDateResponse.getDate());
            assertEquals(getDelinquencyTagsHistory.get(0).getLiftedOnDate(), businessDateResponse.getDate());
            assertEquals(getDelinquencyTagsHistory.get(0).getDelinquencyRange().getClassification(), classificationExpected);
            log.info("Delinquency Tag Item with Lifted On {}", getDelinquencyTagsHistory.get(0).getLiftedOnDate());
        } finally {
            GlobalConfigurationHelper.updateIsBusinessDateEnabled(REQUEST_SPEC, RESPONSE_SPEC, Boolean.FALSE);
        }
    }

    @Test
    public void testLoanClassificationRealtimeWithCharges() {
        try {
            GlobalConfigurationHelper.updateIsBusinessDateEnabled(REQUEST_SPEC, RESPONSE_SPEC, Boolean.TRUE);

            final LocalDate bussinesLocalDate = Utils.getDateAsLocalDate("01 April 2012");
            log.info("Current date {}", bussinesLocalDate);
            BusinessDateHelper.updateBusinessDate(REQUEST_SPEC, RESPONSE_SPEC, BusinessDateType.BUSINESS_DATE, bussinesLocalDate);

            // Given
            final LoanTransactionHelper loanTransactionHelper = new LoanTransactionHelper(this.REQUEST_SPEC, this.RESPONSE_SPEC);

            ArrayList<Integer> rangeIds = new ArrayList<>();
            // First Range
            String jsonRange = DelinquencyRangesHelper.getAsJSON(1, 3);
            PostDelinquencyRangeResponse delinquencyRangeResponse = DelinquencyRangesHelper.createDelinquencyRange(REQUEST_SPEC,
                    RESPONSE_SPEC, jsonRange);
            rangeIds.add(delinquencyRangeResponse.getResourceId());
            jsonRange = DelinquencyRangesHelper.getAsJSON(4, 60);

            GetDelinquencyRangesResponse range = DelinquencyRangesHelper.getDelinquencyRange(REQUEST_SPEC, RESPONSE_SPEC,
                    delinquencyRangeResponse.getResourceId());

            // Second Range
            delinquencyRangeResponse = DelinquencyRangesHelper.createDelinquencyRange(REQUEST_SPEC, RESPONSE_SPEC, jsonRange);
            rangeIds.add(delinquencyRangeResponse.getResourceId());

            range = DelinquencyRangesHelper.getDelinquencyRange(REQUEST_SPEC, RESPONSE_SPEC, delinquencyRangeResponse.getResourceId());
            final String classificationExpected = range.getClassification();
            log.info("Expected Delinquency Range classification after Disbursement {}", classificationExpected);

            String jsonBucket = DelinquencyBucketsHelper.getAsJSON(rangeIds);
            PostDelinquencyBucketResponse delinquencyBucketResponse = DelinquencyBucketsHelper.createDelinquencyBucket(REQUEST_SPEC,
                    RESPONSE_SPEC, jsonBucket);
            assertNotNull(delinquencyBucketResponse);
            final GetDelinquencyBucketsResponse delinquencyBucket = DelinquencyBucketsHelper.getDelinquencyBucket(REQUEST_SPEC,
                    RESPONSE_SPEC, delinquencyBucketResponse.getResourceId());

            // Client and Loan account creation
            final Integer clientId = ClientHelper.createClient(this.REQUEST_SPEC, this.RESPONSE_SPEC, "01 January 2012");
            final GetLoanProductsProductIdResponse getLoanProductsProductResponse = createLoanProduct(loanTransactionHelper,
                    Math.toIntExact(delinquencyBucket.getId()), null);
            assertNotNull(getLoanProductsProductResponse);
            log.info("Loan Product Bucket Name: {}", getLoanProductsProductResponse.getDelinquencyBucket().getName());
            assertEquals(getLoanProductsProductResponse.getDelinquencyBucket().getName(), delinquencyBucket.getName());

            // Older date to have more than one overdue installment
            LocalDate transactionDate = bussinesLocalDate.minusMonths(2).minusDays(5);
            String operationDate = Utils.dateFormatter.format(transactionDate);

            // Create Loan Account
            final Integer loanId = createLoanAccount(loanTransactionHelper, clientId.toString(),
                    getLoanProductsProductResponse.getId().toString(), operationDate, null);

            GetLoansLoanIdResponse getLoansLoanIdResponse = loanTransactionHelper.getLoan(REQUEST_SPEC, RESPONSE_SPEC, loanId);
            log.info("Loan Delinquency Range after Disbursement {}", getLoansLoanIdResponse.getDelinquencyRange().getClassification());
            assertNotNull(getLoansLoanIdResponse);
            // First Loan Delinquency Classification after Disbursement command
            assertEquals(getLoansLoanIdResponse.getDelinquencyRange().getClassification(), classificationExpected);
            loanTransactionHelper.printRepaymentSchedule(getLoansLoanIdResponse);

            // Apply a repayment to get a full paid installment
            operationDate = Utils.dateFormatter.format(bussinesLocalDate);
            loanTransactionHelper.makeLoanRepayment(operationDate, 2049.99f, loanId);

            getLoansLoanIdResponse = loanTransactionHelper.getLoan(REQUEST_SPEC, RESPONSE_SPEC, loanId);
            assertNotNull(getLoansLoanIdResponse);
            // The Loan Delinquency Classification after Repayment command must be null
            log.info("Loan Delinquency Range after Repayment {}", getLoansLoanIdResponse.getDelinquencyRange());
            assertNull(getLoansLoanIdResponse.getDelinquencyRange());
            loanTransactionHelper.printRepaymentSchedule(getLoansLoanIdResponse);

            transactionDate = bussinesLocalDate.minusDays(18);
            operationDate = Utils.dateFormatter.format(transactionDate);

            // Create and apply Charge for Specific Due Date
            final Integer chargeId = ChargesHelper.createCharges(REQUEST_SPEC, RESPONSE_SPEC,
                    ChargesHelper.getLoanSpecifiedDueDateJSON(1, "30", false));
            assertNotNull(chargeId);
            final Integer loanChargeId = loanTransactionHelper.addChargesForLoan(loanId, getChargeApplyJSON(chargeId, operationDate),
                    RESPONSE_SPEC);
            assertNotNull(loanChargeId);

            getLoansLoanIdResponse = loanTransactionHelper.getLoan(REQUEST_SPEC, RESPONSE_SPEC, loanId);
            loanTransactionHelper.printRepaymentSchedule(getLoansLoanIdResponse);

            log.info("Loan Delinquency Range after add Loan Charge {}", getLoansLoanIdResponse.getDelinquencyRange());
            assertNotNull(getLoansLoanIdResponse.getDelinquencyRange());
            // Evaluate a Delinquency Tag set after add charge to the Loan
            assertEquals(getLoansLoanIdResponse.getDelinquencyRange().getClassification(), classificationExpected);
        } finally {
            GlobalConfigurationHelper.updateIsBusinessDateEnabled(REQUEST_SPEC, RESPONSE_SPEC, Boolean.FALSE);
        }
    }

    @Test
    public void testLoanClassificationRealtimeOlderLoan() {

        // Given
        final LoanTransactionHelper loanTransactionHelper = new LoanTransactionHelper(this.REQUEST_SPEC, this.RESPONSE_SPEC);

        ArrayList<Integer> rangeIds = new ArrayList<>();
        // First Range
        String jsonRange = DelinquencyRangesHelper.getAsJSON(4, 30);
        PostDelinquencyRangeResponse delinquencyRangeResponse = DelinquencyRangesHelper.createDelinquencyRange(REQUEST_SPEC, RESPONSE_SPEC,
                jsonRange);
        rangeIds.add(delinquencyRangeResponse.getResourceId());
        GetDelinquencyRangesResponse range = DelinquencyRangesHelper.getDelinquencyRange(REQUEST_SPEC, RESPONSE_SPEC,
                delinquencyRangeResponse.getResourceId());
        final String classificationExpected02 = range.getClassification();
        log.info("Expected Delinquency Range classification after first repayment {}", classificationExpected02);

        // Second Range
        jsonRange = DelinquencyRangesHelper.getAsJSON(31, 60);
        delinquencyRangeResponse = DelinquencyRangesHelper.createDelinquencyRange(REQUEST_SPEC, RESPONSE_SPEC, jsonRange);
        rangeIds.add(delinquencyRangeResponse.getResourceId());

        range = DelinquencyRangesHelper.getDelinquencyRange(REQUEST_SPEC, RESPONSE_SPEC, delinquencyRangeResponse.getResourceId());
        final String classificationExpected01 = range.getClassification();
        log.info("Expected Delinquency Range classification after Disbursement {}", classificationExpected01);

        // Third Range
        jsonRange = DelinquencyRangesHelper.getAsJSON(61, 90);
        delinquencyRangeResponse = DelinquencyRangesHelper.createDelinquencyRange(REQUEST_SPEC, RESPONSE_SPEC, jsonRange);
        rangeIds.add(delinquencyRangeResponse.getResourceId());

        range = DelinquencyRangesHelper.getDelinquencyRange(REQUEST_SPEC, RESPONSE_SPEC, delinquencyRangeResponse.getResourceId());

        String jsonBucket = DelinquencyBucketsHelper.getAsJSON(rangeIds);
        PostDelinquencyBucketResponse delinquencyBucketResponse = DelinquencyBucketsHelper.createDelinquencyBucket(REQUEST_SPEC,
                RESPONSE_SPEC, jsonBucket);
        assertNotNull(delinquencyBucketResponse);
        final GetDelinquencyBucketsResponse delinquencyBucket = DelinquencyBucketsHelper.getDelinquencyBucket(REQUEST_SPEC, RESPONSE_SPEC,
                delinquencyBucketResponse.getResourceId());

        // Client and Loan account creation
        final Integer clientId = ClientHelper.createClient(this.REQUEST_SPEC, this.RESPONSE_SPEC, "01 January 2012");
        final GetLoanProductsProductIdResponse getLoanProductsProductResponse = createLoanProduct(loanTransactionHelper,
                Math.toIntExact(delinquencyBucket.getId()), null);
        assertNotNull(getLoanProductsProductResponse);
        log.info("Loan Product Bucket Name: {}", getLoanProductsProductResponse.getDelinquencyBucket().getName());
        assertEquals(getLoanProductsProductResponse.getDelinquencyBucket().getName(), delinquencyBucket.getName());

        final LocalDate todaysDate = Utils.getLocalDateOfTenant();
        // Older date to have more than one overdue installment
        LocalDate transactionDate = todaysDate.minusDays(85);
        String operationDate = Utils.dateFormatter.format(transactionDate);

        // Create Loan Account
        final Integer loanId = createLoanAccount(loanTransactionHelper, clientId.toString(),
                getLoanProductsProductResponse.getId().toString(), operationDate, null);

        GetLoansLoanIdResponse getLoansLoanIdResponse = loanTransactionHelper.getLoan(REQUEST_SPEC, RESPONSE_SPEC, loanId);
        assertNotNull(getLoansLoanIdResponse);
        log.info("Loan Delinquency Range after Disbursement in null? {}", (getLoansLoanIdResponse.getDelinquencyRange() == null));
        assertNotNull(getLoansLoanIdResponse.getDelinquencyRange());
        log.info("Loan Delinquency Range after Disbursement {}", getLoansLoanIdResponse.getDelinquencyRange());
        // First Loan Delinquency Classification after Disbursement command
        assertEquals(getLoansLoanIdResponse.getDelinquencyRange().getClassification(), classificationExpected01);

        loanTransactionHelper.printRepaymentSchedule(getLoansLoanIdResponse);

        // Apply a repayment to get a first full paid installment
        transactionDate = todaysDate.minusDays(1);
        operationDate = Utils.dateFormatter.format(transactionDate);
        PostLoansLoanIdTransactionsResponse loansLoanIdTransactions = loanTransactionHelper.makeLoanRepayment(operationDate, 1050.0f,
                loanId);
        assertNotNull(loansLoanIdTransactions);
        log.info("Loan repayment transaction id {}", loansLoanIdTransactions.getResourceId());
        getLoansLoanIdResponse = loanTransactionHelper.getLoan(REQUEST_SPEC, RESPONSE_SPEC, loanId);
        log.info("Loan Delinquency Range after first Repayment {}", getLoansLoanIdResponse.getDelinquencyRange());
        assertNotNull(getLoansLoanIdResponse.getDelinquencyRange());
        // First Loan Delinquency Classification remains after Repayment because the installment is not fully paid
        assertEquals(getLoansLoanIdResponse.getDelinquencyRange().getClassification(), classificationExpected02);

        ArrayList<GetDelinquencyTagHistoryResponse> getDelinquencyTagsHistory = loanTransactionHelper.getLoanDelinquencyTags(REQUEST_SPEC,
                RESPONSE_SPEC, loanId);
        assertNotNull(getDelinquencyTagsHistory);
        log.info("Delinquency Tag History items {}", getDelinquencyTagsHistory.size());
        log.info("Delinquency Tag Item with Lifted On {}", getDelinquencyTagsHistory.get(0).getLiftedOnDate());
        assertEquals(getDelinquencyTagsHistory.get(0).getAddedOnDate(), Utils.getLocalDateOfTenant());
        assertEquals(getDelinquencyTagsHistory.get(0).getLiftedOnDate(), Utils.getLocalDateOfTenant());
        assertEquals(getDelinquencyTagsHistory.get(0).getDelinquencyRange().getClassification(), classificationExpected01);
        log.info("Loan Id {} with Loan status {}", getLoansLoanIdResponse.getId(), getLoansLoanIdResponse.getStatus().getCode());

        // Apply a repayment to get a second full paid installment
        loansLoanIdTransactions = loanTransactionHelper.makeLoanRepayment(operationDate, 1020.0f, loanId);
        assertNotNull(loansLoanIdTransactions);
        log.info("Loan repayment transaction id {}", loansLoanIdTransactions.getResourceId());
        getLoansLoanIdResponse = loanTransactionHelper.getLoan(REQUEST_SPEC, RESPONSE_SPEC, loanId);
        log.info("Loan Delinquency Range after second Repayment {}", getLoansLoanIdResponse.getDelinquencyRange());
        assertNotNull(getLoansLoanIdResponse);
        // The Loan Delinquency Classification after Repayment command must be null
        assertNull(getLoansLoanIdResponse.getDelinquencyRange());

        getDelinquencyTagsHistory = loanTransactionHelper.getLoanDelinquencyTags(REQUEST_SPEC, RESPONSE_SPEC, loanId);
        assertNotNull(getDelinquencyTagsHistory);
        log.info("Delinquency Tag History items {}", getDelinquencyTagsHistory.size());
        log.info("Delinquency Tag Item with Lifted On {}", getDelinquencyTagsHistory.get(1).getLiftedOnDate());
        assertEquals(getDelinquencyTagsHistory.get(1).getAddedOnDate(), Utils.getLocalDateOfTenant());
        assertEquals(getDelinquencyTagsHistory.get(1).getLiftedOnDate(), Utils.getLocalDateOfTenant());
        assertEquals(getDelinquencyTagsHistory.get(1).getDelinquencyRange().getClassification(), classificationExpected02);
        log.info("Loan Id {} with final Loan status {}", getLoansLoanIdResponse.getId(), getLoansLoanIdResponse.getStatus().getCode());
    }

    @Test
    public void testLoanClassificationRealtimeWithReversedRepayment() {
        // Given
        final LoanTransactionHelper loanTransactionHelper = new LoanTransactionHelper(this.REQUEST_SPEC, this.RESPONSE_SPEC);

        ArrayList<Integer> rangeIds = new ArrayList<>();
        // First Range
        String jsonRange = DelinquencyRangesHelper.getAsJSON(4, 30);
        PostDelinquencyRangeResponse delinquencyRangeResponse = DelinquencyRangesHelper.createDelinquencyRange(REQUEST_SPEC, RESPONSE_SPEC,
                jsonRange);
        rangeIds.add(delinquencyRangeResponse.getResourceId());
        GetDelinquencyRangesResponse range = DelinquencyRangesHelper.getDelinquencyRange(REQUEST_SPEC, RESPONSE_SPEC,
                delinquencyRangeResponse.getResourceId());
        final String classificationExpected = range.getClassification();
        log.info("Expected Delinquency Range classification after first repayment {}", classificationExpected);

        String jsonBucket = DelinquencyBucketsHelper.getAsJSON(rangeIds);
        PostDelinquencyBucketResponse delinquencyBucketResponse = DelinquencyBucketsHelper.createDelinquencyBucket(REQUEST_SPEC,
                RESPONSE_SPEC, jsonBucket);
        assertNotNull(delinquencyBucketResponse);
        final GetDelinquencyBucketsResponse delinquencyBucket = DelinquencyBucketsHelper.getDelinquencyBucket(REQUEST_SPEC, RESPONSE_SPEC,
                delinquencyBucketResponse.getResourceId());

        // Client and Loan account creation
        final Integer clientId = ClientHelper.createClient(this.REQUEST_SPEC, this.RESPONSE_SPEC, "01 January 2012");
        final GetLoanProductsProductIdResponse getLoanProductsProductResponse = createLoanProduct(loanTransactionHelper,
                Math.toIntExact(delinquencyBucket.getId()), null);
        assertNotNull(getLoanProductsProductResponse);
        log.info("Loan Product Bucket Name: {}", getLoanProductsProductResponse.getDelinquencyBucket().getName());
        assertEquals(getLoanProductsProductResponse.getDelinquencyBucket().getName(), delinquencyBucket.getName());

        final LocalDate todaysDate = Utils.getLocalDateOfTenant();
        log.info("Local date of Tenant: {}", todaysDate);

        // Older date to have more than one overdue installment
        final LocalDate transactionDate = todaysDate.minusDays(50);
        String operationDate = Utils.dateFormatter.format(transactionDate);

        // Create Loan Account
        final Integer loanId = createLoanAccount(loanTransactionHelper, clientId.toString(),
                getLoanProductsProductResponse.getId().toString(), operationDate, null);

        GetLoansLoanIdResponse getLoansLoanIdResponse = loanTransactionHelper.getLoan(REQUEST_SPEC, RESPONSE_SPEC, loanId);
        loanTransactionHelper.printRepaymentSchedule(getLoansLoanIdResponse);
        loanTransactionHelper.printDelinquencyData(getLoansLoanIdResponse);

        log.info("Loan Delinquency Range after Disbursement in null? {}", (getLoansLoanIdResponse.getDelinquencyRange() == null));
        assertNotNull(getLoansLoanIdResponse);
        assertNotNull(getLoansLoanIdResponse.getDelinquencyRange());
        log.info("Loan Delinquency Range after Disbursement {}", getLoansLoanIdResponse.getDelinquencyRange());
        // First Loan Delinquency Classification after Disbursement command
        assertEquals(getLoansLoanIdResponse.getDelinquencyRange().getClassification(), classificationExpected);

        loanTransactionHelper.printRepaymentSchedule(getLoansLoanIdResponse);

        // Apply a repayment to get a full paid installment
        operationDate = Utils.dateFormatter.format(todaysDate);
        PostLoansLoanIdTransactionsResponse loansLoanIdTransactions = loanTransactionHelper.makeLoanRepayment(operationDate, 1050.0f,
                loanId);
        assertNotNull(loansLoanIdTransactions);
        log.info("Loan repayment transaction id {}", loansLoanIdTransactions.getResourceId());
        getLoansLoanIdResponse = loanTransactionHelper.getLoan(REQUEST_SPEC, RESPONSE_SPEC, loanId);
        log.info("Loan Delinquency Range after Repayment {}", getLoansLoanIdResponse.getDelinquencyRange());
        // Loan Delinquency Classification removed after Repayment because the installment is fully paid
        assertNull(getLoansLoanIdResponse.getDelinquencyRange());

        ArrayList<GetDelinquencyTagHistoryResponse> getDelinquencyTagsHistory = loanTransactionHelper.getLoanDelinquencyTags(REQUEST_SPEC,
                RESPONSE_SPEC, loanId);
        assertNotNull(getDelinquencyTagsHistory);
        log.info("Delinquency Tag History items {}", getDelinquencyTagsHistory.size());
        log.info("Delinquency Tag Item with Lifted On {}", getDelinquencyTagsHistory.get(0).getLiftedOnDate());
        assertEquals(getDelinquencyTagsHistory.get(0).getAddedOnDate(), Utils.getLocalDateOfTenant());
        assertEquals(getDelinquencyTagsHistory.get(0).getLiftedOnDate(), Utils.getLocalDateOfTenant());
        assertEquals(getDelinquencyTagsHistory.get(0).getDelinquencyRange().getClassification(), classificationExpected);
        log.info("Loan Id {} with Loan status {}", getLoansLoanIdResponse.getId(), getLoansLoanIdResponse.getStatus().getCode());

        // Reverse the Previous Loan Repayment
        PostLoansLoanIdTransactionsResponse loansLoanIdReverseTransactions = loanTransactionHelper.reverseLoanTransaction(loanId,
                loansLoanIdTransactions.getResourceId(), operationDate, RESPONSE_SPEC);
        assertNotNull(loansLoanIdReverseTransactions);
        log.info("Loan repayment reverse transaction id {}", loansLoanIdReverseTransactions.getResourceId());
        getLoansLoanIdResponse = loanTransactionHelper.getLoan(REQUEST_SPEC, RESPONSE_SPEC, loanId);
        log.info("Loan Delinquency Range after Reverse Repayment {}", getLoansLoanIdResponse.getDelinquencyRange());
        // Loan Delinquency Classification goes back after Repayment because the installment is not paid
        assertEquals(getLoansLoanIdResponse.getDelinquencyRange().getClassification(), classificationExpected);

        getDelinquencyTagsHistory = loanTransactionHelper.getLoanDelinquencyTags(REQUEST_SPEC, RESPONSE_SPEC, loanId);
        assertNotNull(getDelinquencyTagsHistory);
        log.info("Delinquency Tag History items {}", getDelinquencyTagsHistory.size());
        log.info("Delinquency Tag Item with Lifted On {}", getDelinquencyTagsHistory.get(1).getLiftedOnDate());
        assertEquals(getDelinquencyTagsHistory.get(1).getAddedOnDate(), Utils.getLocalDateOfTenant());
        // Second record is open with liftedOn in null
        assertNull(getDelinquencyTagsHistory.get(1).getLiftedOnDate());
        assertEquals(getDelinquencyTagsHistory.get(1).getDelinquencyRange().getClassification(), classificationExpected);
        log.info("Loan Id {} with final Loan status {}", getLoansLoanIdResponse.getId(), getLoansLoanIdResponse.getStatus().getCode());
    }

    @Test
    public void testLoanClassificationJob() {
        try {
            GlobalConfigurationHelper.updateIsBusinessDateEnabled(REQUEST_SPEC, RESPONSE_SPEC, Boolean.TRUE);

            LocalDate businessDate = Utils.getLocalDateOfTenant();
            businessDate = businessDate.minusDays(37);
            log.info("Current date {}", businessDate);
            BusinessDateHelper.updateBusinessDate(REQUEST_SPEC, RESPONSE_SPEC, BusinessDateType.BUSINESS_DATE, businessDate);

            final LoanTransactionHelper loanTransactionHelper = new LoanTransactionHelper(this.REQUEST_SPEC, this.RESPONSE_SPEC);
            final SchedulerJobHelper schedulerJobHelper = new SchedulerJobHelper(REQUEST_SPEC);

            ArrayList<Integer> rangeIds = new ArrayList<>();
            String jsonRange = DelinquencyRangesHelper.getAsJSON(1, 3);
            PostDelinquencyRangeResponse delinquencyRangeResponse = DelinquencyRangesHelper.createDelinquencyRange(REQUEST_SPEC,
                    RESPONSE_SPEC, jsonRange);
            rangeIds.add(delinquencyRangeResponse.getResourceId());
            jsonRange = DelinquencyRangesHelper.getAsJSON(4, 60);
            // Create
            delinquencyRangeResponse = DelinquencyRangesHelper.createDelinquencyRange(REQUEST_SPEC, RESPONSE_SPEC, jsonRange);
            rangeIds.add(delinquencyRangeResponse.getResourceId());

            final GetDelinquencyRangesResponse range = DelinquencyRangesHelper.getDelinquencyRange(REQUEST_SPEC, RESPONSE_SPEC,
                    delinquencyRangeResponse.getResourceId());
            final String classificationExpected = range.getClassification();
            log.info("Expected Delinquency Range classification {}", classificationExpected);

            String jsonBucket = DelinquencyBucketsHelper.getAsJSON(rangeIds);
            PostDelinquencyBucketResponse delinquencyBucketResponse = DelinquencyBucketsHelper.createDelinquencyBucket(REQUEST_SPEC,
                    RESPONSE_SPEC, jsonBucket);
            final GetDelinquencyBucketsResponse delinquencyBucket = DelinquencyBucketsHelper.getDelinquencyBucket(REQUEST_SPEC,
                    RESPONSE_SPEC, delinquencyBucketResponse.getResourceId());

            // Client and Loan account creation
            final Integer clientId = ClientHelper.createClient(this.REQUEST_SPEC, this.RESPONSE_SPEC, "01 January 2012");
            final GetLoanProductsProductIdResponse getLoanProductsProductResponse = createLoanProduct(loanTransactionHelper,
                    Math.toIntExact(delinquencyBucket.getId()), null);
            assertNotNull(getLoanProductsProductResponse);
            log.info("Loan Product Bucket Name: {}", getLoanProductsProductResponse.getDelinquencyBucket().getName());
            assertEquals(getLoanProductsProductResponse.getDelinquencyBucket().getName(), delinquencyBucket.getName());

            final LocalDate todaysDate = Utils.getLocalDateOfTenant();
            // Older date to have more than one overdue installment
            final LocalDate transactionDate = todaysDate.minusDays(57);
            String operationDate = Utils.dateFormatter.format(transactionDate);

            // Create Loan Account
            final Integer loanId = createLoanAccount(loanTransactionHelper, clientId.toString(),
                    getLoanProductsProductResponse.getId().toString(), operationDate, null);

            // Run first time the Job
            final String jobName = "Loan Delinquency Classification";
            schedulerJobHelper.executeAndAwaitJob(jobName);

            // Get loan details expecting to have not a delinquency classification
            GetLoansLoanIdResponse getLoansLoanIdResponse = loanTransactionHelper.getLoan(REQUEST_SPEC, RESPONSE_SPEC, loanId);
            loanTransactionHelper.printDelinquencyData(getLoansLoanIdResponse);
            final GetDelinquencyRangesResponse firstTestCase = getLoansLoanIdResponse.getDelinquencyRange();
            log.info("Loan Delinquency Range is null {}", (firstTestCase == null));
            loanTransactionHelper.printRepaymentSchedule(getLoansLoanIdResponse);

            // Move the Business date to get older the loan and to have an overdue loan
            businessDate = businessDate.plusMonths(1);
            log.info("Current date {}", businessDate);
            BusinessDateHelper.updateBusinessDate(REQUEST_SPEC, RESPONSE_SPEC, BusinessDateType.BUSINESS_DATE, businessDate);
            // Run Second time the Job
            schedulerJobHelper.executeAndAwaitJob(jobName);

            // Get loan details expecting to have a delinquency classification
            getLoansLoanIdResponse = loanTransactionHelper.getLoan(REQUEST_SPEC, RESPONSE_SPEC, loanId);
            loanTransactionHelper.printRepaymentSchedule(getLoansLoanIdResponse);
            loanTransactionHelper.printDelinquencyData(getLoansLoanIdResponse);

            final GetDelinquencyRangesResponse secondTestCase = getLoansLoanIdResponse.getDelinquencyRange();
            assertNotNull(secondTestCase);
            log.info("Loan Delinquency Range is {}", secondTestCase.getClassification());

            // Then
            assertNotNull(delinquencyBucketResponse);
            assertNotNull(getLoanProductsProductResponse);
            assertNull(firstTestCase);
            assertEquals(getLoanProductsProductResponse.getDelinquencyBucket().getName(), delinquencyBucket.getName());
            assertEquals(secondTestCase.getClassification(), classificationExpected);

        } finally {
            GlobalConfigurationHelper.updateIsBusinessDateEnabled(REQUEST_SPEC, RESPONSE_SPEC, Boolean.FALSE);
        }
    }

    @Test
    public void testLoanClassificationStepAsPartOfCOB() {
        try {
            GlobalConfigurationHelper.updateIsBusinessDateEnabled(REQUEST_SPEC, RESPONSE_SPEC, Boolean.TRUE);

            LocalDate bussinesLocalDate = Utils.getDateAsLocalDate("01 April 2012");
            log.info("Current date {}", bussinesLocalDate);
            BusinessDateHelper.updateBusinessDate(REQUEST_SPEC, RESPONSE_SPEC, BusinessDateType.BUSINESS_DATE, bussinesLocalDate);

            // Given
            final LoanTransactionHelper loanTransactionHelper = new LoanTransactionHelper(this.REQUEST_SPEC, this.RESPONSE_SPEC);
            final SchedulerJobHelper schedulerJobHelper = new SchedulerJobHelper(REQUEST_SPEC);

            ArrayList<Integer> rangeIds = new ArrayList<>();
            String jsonRange = DelinquencyRangesHelper.getAsJSON(1, 3);
            PostDelinquencyRangeResponse delinquencyRangeResponse = DelinquencyRangesHelper.createDelinquencyRange(REQUEST_SPEC,
                    RESPONSE_SPEC, jsonRange);
            rangeIds.add(delinquencyRangeResponse.getResourceId());
            final GetDelinquencyRangesResponse range = DelinquencyRangesHelper.getDelinquencyRange(REQUEST_SPEC, RESPONSE_SPEC,
                    delinquencyRangeResponse.getResourceId());
            final String classificationExpected = range.getClassification();
            log.info("Expected Delinquency Range classification {}", classificationExpected);

            jsonRange = DelinquencyRangesHelper.getAsJSON(4, 60);
            delinquencyRangeResponse = DelinquencyRangesHelper.createDelinquencyRange(REQUEST_SPEC, RESPONSE_SPEC, jsonRange);
            rangeIds.add(delinquencyRangeResponse.getResourceId());

            String jsonBucket = DelinquencyBucketsHelper.getAsJSON(rangeIds);
            PostDelinquencyBucketResponse delinquencyBucketResponse = DelinquencyBucketsHelper.createDelinquencyBucket(REQUEST_SPEC,
                    RESPONSE_SPEC, jsonBucket);
            final GetDelinquencyBucketsResponse delinquencyBucket = DelinquencyBucketsHelper.getDelinquencyBucket(REQUEST_SPEC,
                    RESPONSE_SPEC, delinquencyBucketResponse.getResourceId());

            // Client and Loan account creation
            final Integer clientId = ClientHelper.createClient(this.REQUEST_SPEC, this.RESPONSE_SPEC, "01 January 2012");
            final GetLoanProductsProductIdResponse getLoanProductsProductResponse = createLoanProduct(loanTransactionHelper,
                    Math.toIntExact(delinquencyBucket.getId()), null);
            assertNotNull(getLoanProductsProductResponse);
            log.info("Loan Product Bucket Name: {}", getLoanProductsProductResponse.getDelinquencyBucket().getName());
            assertEquals(getLoanProductsProductResponse.getDelinquencyBucket().getName(), delinquencyBucket.getName());

            // Older date to have more than one overdue installment
            final LocalDate transactionDate = bussinesLocalDate.minusDays(31);
            String operationDate = Utils.dateFormatter.format(transactionDate);

            // Create Loan Account
            final Integer loanId = createLoanAccount(loanTransactionHelper, clientId.toString(),
                    getLoanProductsProductResponse.getId().toString(), operationDate, null);

            // COB Step Validation
            final JobBusinessStepConfigData jobBusinessStepConfigData = BusinessStepConfigurationHelper
                    .getConfiguredBusinessStepsByJobName(REQUEST_SPEC, RESPONSE_SPEC, BusinessConfigurationApiTest.LOAN_JOB_NAME);
            assertNotNull(jobBusinessStepConfigData);
            assertEquals(BusinessConfigurationApiTest.LOAN_JOB_NAME, jobBusinessStepConfigData.getJobName());
            assertTrue(jobBusinessStepConfigData.getBusinessSteps().size() > 0);
            assertTrue(jobBusinessStepConfigData.getBusinessSteps().stream().anyMatch(
                    businessStep -> BusinessConfigurationApiTest.LOAN_DELINQUENCY_CLASSIFICATION.equals(businessStep.getStepName())));

            // Run first time the Loan COB Job
            final String jobName = "Loan COB";
            schedulerJobHelper.executeAndAwaitJob(jobName);

            // Get loan details expecting to have not a delinquency classification
            GetLoansLoanIdResponse getLoansLoanIdResponse = loanTransactionHelper.getLoan(REQUEST_SPEC, RESPONSE_SPEC, loanId);
            final GetDelinquencyRangesResponse firstTestCase = getLoansLoanIdResponse.getDelinquencyRange();
            log.info("Loan Delinquency Range is null {}", (firstTestCase == null));
            GetLoansLoanIdRepaymentSchedule getLoanRepaymentSchedule = getLoansLoanIdResponse.getRepaymentSchedule();
            if (getLoanRepaymentSchedule != null) {
                log.info("Loan with {} periods", getLoanRepaymentSchedule.getPeriods().size());
                for (GetLoansLoanIdRepaymentPeriod period : getLoanRepaymentSchedule.getPeriods()) {
                    log.info("Period number {} for due date {} and outstanding {}", period.getPeriod(), period.getDueDate(),
                            period.getTotalOutstandingForPeriod());
                }
            }

            // Move the Business date to get older the loan and to have an overdue loan
            LocalDate lastLoanCOBBusinessDate = bussinesLocalDate;
            bussinesLocalDate = bussinesLocalDate.plusDays(3);
            schedulerJobHelper.fastForwardTime(lastLoanCOBBusinessDate, bussinesLocalDate, jobName, RESPONSE_SPEC);
            log.info("Current date {}", bussinesLocalDate);
            BusinessDateHelper.updateBusinessDate(REQUEST_SPEC, RESPONSE_SPEC, BusinessDateType.BUSINESS_DATE, bussinesLocalDate);
            // Run Second time the Job
            schedulerJobHelper.executeAndAwaitJob(jobName);

            // Get loan details expecting to have a delinquency classification
            getLoansLoanIdResponse = loanTransactionHelper.getLoan(REQUEST_SPEC, RESPONSE_SPEC, loanId);
            loanTransactionHelper.printRepaymentSchedule(getLoansLoanIdResponse);
            final GetDelinquencyRangesResponse secondTestCase = getLoansLoanIdResponse.getDelinquencyRange();
            assertNotNull(secondTestCase);
            log.info("Loan Delinquency Range is {}", secondTestCase.getClassification());

            // Then
            assertNotNull(delinquencyBucketResponse);
            assertNotNull(getLoanProductsProductResponse);
            assertNull(firstTestCase);
            assertEquals(getLoanProductsProductResponse.getDelinquencyBucket().getName(), delinquencyBucket.getName());
            assertEquals(secondTestCase.getClassification(), classificationExpected);
        } finally {
            GlobalConfigurationHelper.updateIsBusinessDateEnabled(REQUEST_SPEC, RESPONSE_SPEC, Boolean.FALSE);
        }
    }

    @Test
    public void testLoanClassificationToValidateNegatives() {
        try {
            GlobalConfigurationHelper.updateIsBusinessDateEnabled(REQUEST_SPEC, RESPONSE_SPEC, Boolean.TRUE);

            LocalDate bussinesLocalDate = Utils.getDateAsLocalDate("01 January 2012");
            log.info("Current date {}", bussinesLocalDate);
            BusinessDateHelper.updateBusinessDate(REQUEST_SPEC, RESPONSE_SPEC, BusinessDateType.BUSINESS_DATE, bussinesLocalDate);

            // Given
            final LoanTransactionHelper loanTransactionHelper = new LoanTransactionHelper(this.REQUEST_SPEC, this.RESPONSE_SPEC);
            final SchedulerJobHelper schedulerJobHelper = new SchedulerJobHelper(REQUEST_SPEC);

            ArrayList<Integer> rangeIds = new ArrayList<>();
            String jsonRange = DelinquencyRangesHelper.getAsJSON(1, 3);
            PostDelinquencyRangeResponse delinquencyRangeResponse = DelinquencyRangesHelper.createDelinquencyRange(REQUEST_SPEC,
                    RESPONSE_SPEC, jsonRange);
            rangeIds.add(delinquencyRangeResponse.getResourceId());
            final GetDelinquencyRangesResponse range = DelinquencyRangesHelper.getDelinquencyRange(REQUEST_SPEC, RESPONSE_SPEC,
                    delinquencyRangeResponse.getResourceId());
            final String classificationExpected = range.getClassification();
            log.info("Expected Delinquency Range classification {}", classificationExpected);

            jsonRange = DelinquencyRangesHelper.getAsJSON(4, 60);
            delinquencyRangeResponse = DelinquencyRangesHelper.createDelinquencyRange(REQUEST_SPEC, RESPONSE_SPEC, jsonRange);
            rangeIds.add(delinquencyRangeResponse.getResourceId());

            String jsonBucket = DelinquencyBucketsHelper.getAsJSON(rangeIds);
            PostDelinquencyBucketResponse delinquencyBucketResponse = DelinquencyBucketsHelper.createDelinquencyBucket(REQUEST_SPEC,
                    RESPONSE_SPEC, jsonBucket);
            final GetDelinquencyBucketsResponse delinquencyBucket = DelinquencyBucketsHelper.getDelinquencyBucket(REQUEST_SPEC,
                    RESPONSE_SPEC, delinquencyBucketResponse.getResourceId());

            // Client and Loan account creation
            final Integer clientId = ClientHelper.createClient(this.REQUEST_SPEC, this.RESPONSE_SPEC, "01 January 2012");
            final GetLoanProductsProductIdResponse getLoanProductsProductResponse = createLoanProduct(loanTransactionHelper,
                    Math.toIntExact(delinquencyBucket.getId()), "3");
            assertNotNull(getLoanProductsProductResponse);
            log.info("Loan Product Bucket Name: {}", getLoanProductsProductResponse.getDelinquencyBucket().getName());
            assertEquals(getLoanProductsProductResponse.getDelinquencyBucket().getName(), delinquencyBucket.getName());

            // Older date to have more than one overdue installment
            final LocalDate transactionDate = bussinesLocalDate;
            String operationDate = Utils.dateFormatter.format(transactionDate);

            // Create Loan Account
            final Integer loanId = createLoanAccount(loanTransactionHelper, clientId.toString(),
                    getLoanProductsProductResponse.getId().toString(), operationDate, null);

            // Get loan details expecting to have a delinquency classification
            GetLoansLoanIdResponse getLoansLoanIdResponse = loanTransactionHelper.getLoan(REQUEST_SPEC, RESPONSE_SPEC, loanId);
            final GetDelinquencyRangesResponse firstTestCase = getLoansLoanIdResponse.getDelinquencyRange();
            log.info("Loan Delinquency Range is null {}", (firstTestCase == null));
            loanTransactionHelper.printRepaymentSchedule(getLoansLoanIdResponse);

            final String jobName = "Loan COB";

            bussinesLocalDate = Utils.getDateAsLocalDate("31 January 2012");
            LocalDate lastLoanCOBBusinessDate = bussinesLocalDate.minusDays(1);
            schedulerJobHelper.fastForwardTime(lastLoanCOBBusinessDate, bussinesLocalDate, jobName, RESPONSE_SPEC);
            log.info("Current date {}", bussinesLocalDate);
            BusinessDateHelper.updateBusinessDate(REQUEST_SPEC, RESPONSE_SPEC, BusinessDateType.BUSINESS_DATE, bussinesLocalDate);
            // Run Second time the Job
            schedulerJobHelper.executeAndAwaitJob(jobName);

            // Get loan details expecting to have a delinquency classification
            getLoansLoanIdResponse = loanTransactionHelper.getLoan(REQUEST_SPEC, RESPONSE_SPEC, loanId);
            loanTransactionHelper.printDelinquencyData(getLoansLoanIdResponse);

            GetLoansLoanIdDelinquencySummary getLoansLoanIdCollectionData = getLoansLoanIdResponse.getDelinquent();
            assertNotNull(getLoansLoanIdCollectionData);
            assertEquals(0, getLoansLoanIdCollectionData.getDelinquentDays());
            assertEquals(0, getLoansLoanIdCollectionData.getPastDueDays());

        } finally {
            GlobalConfigurationHelper.updateIsBusinessDateEnabled(REQUEST_SPEC, RESPONSE_SPEC, Boolean.FALSE);
        }
    }

    @Test
    public void testLoanClassificationUsingAgeingArrears() {
        try {
            GlobalConfigurationHelper.updateIsBusinessDateEnabled(REQUEST_SPEC, RESPONSE_SPEC, Boolean.TRUE);

            LocalDate bussinesLocalDate = Utils.getDateAsLocalDate("01 January 2012");
            log.info("Current date {}", bussinesLocalDate);
            BusinessDateHelper.updateBusinessDate(REQUEST_SPEC, RESPONSE_SPEC, BusinessDateType.BUSINESS_DATE, bussinesLocalDate);

            // Given
            final LoanTransactionHelper loanTransactionHelper = new LoanTransactionHelper(this.REQUEST_SPEC, this.RESPONSE_SPEC);
            final SchedulerJobHelper schedulerJobHelper = new SchedulerJobHelper(REQUEST_SPEC);

            ArrayList<Integer> rangeIds = new ArrayList<>();
            String jsonRange = DelinquencyRangesHelper.getAsJSON(1, 3);
            PostDelinquencyRangeResponse delinquencyRangeResponse = DelinquencyRangesHelper.createDelinquencyRange(REQUEST_SPEC,
                    RESPONSE_SPEC, jsonRange);
            rangeIds.add(delinquencyRangeResponse.getResourceId());
            final GetDelinquencyRangesResponse range = DelinquencyRangesHelper.getDelinquencyRange(REQUEST_SPEC, RESPONSE_SPEC,
                    delinquencyRangeResponse.getResourceId());
            final String classificationExpected = range.getClassification();
            log.info("Expected Delinquency Range classification {}", classificationExpected);

            jsonRange = DelinquencyRangesHelper.getAsJSON(4, 60);
            delinquencyRangeResponse = DelinquencyRangesHelper.createDelinquencyRange(REQUEST_SPEC, RESPONSE_SPEC, jsonRange);
            rangeIds.add(delinquencyRangeResponse.getResourceId());

            String jsonBucket = DelinquencyBucketsHelper.getAsJSON(rangeIds);
            PostDelinquencyBucketResponse delinquencyBucketResponse = DelinquencyBucketsHelper.createDelinquencyBucket(REQUEST_SPEC,
                    RESPONSE_SPEC, jsonBucket);
            final GetDelinquencyBucketsResponse delinquencyBucket = DelinquencyBucketsHelper.getDelinquencyBucket(REQUEST_SPEC,
                    RESPONSE_SPEC, delinquencyBucketResponse.getResourceId());

            // Client and Loan account creation
            final Integer clientId = ClientHelper.createClient(this.REQUEST_SPEC, this.RESPONSE_SPEC, "01 January 2012");
            final GetLoanProductsProductIdResponse getLoanProductsProductResponse = createLoanProduct(loanTransactionHelper,
                    Math.toIntExact(delinquencyBucket.getId()), "3");
            assertNotNull(getLoanProductsProductResponse);
            log.info("Loan Product Arrears: {}", getLoanProductsProductResponse.getInArrearsTolerance());
            assertEquals(3, getLoanProductsProductResponse.getInArrearsTolerance());

            // Older date to have more than one overdue installment
            final LocalDate transactionDate = bussinesLocalDate;
            String operationDate = Utils.dateFormatter.format(transactionDate);

            // Create Loan Account
            final Integer loanId = createLoanAccount(loanTransactionHelper, clientId.toString(),
                    getLoanProductsProductResponse.getId().toString(), operationDate, "3");

            // Get loan details expecting to have a delinquency classification
            GetLoansLoanIdResponse getLoansLoanIdResponse = loanTransactionHelper.getLoan(REQUEST_SPEC, RESPONSE_SPEC, loanId);
            final GetDelinquencyRangesResponse firstTestCase = getLoansLoanIdResponse.getDelinquencyRange();
            log.info("Loan Delinquency Range is null {}", (firstTestCase == null));
            loanTransactionHelper.printRepaymentSchedule(getLoansLoanIdResponse);
            log.info("Loan Account Arrears {}", getLoansLoanIdResponse.getInArrearsTolerance());
            assertEquals(3, getLoansLoanIdResponse.getInArrearsTolerance());

            // Update the Loan Product
            updateLoanProduct(loanTransactionHelper, getLoanProductsProductResponse.getId(), 0);
            GetLoanProductsProductIdResponse loanProductsProductIdResponseUpd = loanTransactionHelper
                    .getLoanProduct(getLoanProductsProductResponse.getId().intValue());
            assertNotNull(loanProductsProductIdResponseUpd);
            log.info("Loan Product Arrears: {}", loanProductsProductIdResponseUpd.getInArrearsTolerance());
            assertEquals(0, loanProductsProductIdResponseUpd.getInArrearsTolerance());

            final String jobName = "Loan COB";

            bussinesLocalDate = Utils.getDateAsLocalDate("31 January 2012");
            LocalDate lastLoanCOBBusinessDate = bussinesLocalDate.minusDays(1);
            schedulerJobHelper.fastForwardTime(lastLoanCOBBusinessDate, bussinesLocalDate, jobName, RESPONSE_SPEC);
            log.info("Current date {}", bussinesLocalDate);
            BusinessDateHelper.updateBusinessDate(REQUEST_SPEC, RESPONSE_SPEC, BusinessDateType.BUSINESS_DATE, bussinesLocalDate);
            // Run Second time the Job
            schedulerJobHelper.executeAndAwaitJob(jobName);

            // Get loan details expecting to have a delinquency classification
            getLoansLoanIdResponse = loanTransactionHelper.getLoan(REQUEST_SPEC, RESPONSE_SPEC, loanId);
            loanTransactionHelper.printDelinquencyData(getLoansLoanIdResponse);

            GetLoansLoanIdDelinquencySummary getLoansLoanIdCollectionData = getLoansLoanIdResponse.getDelinquent();
            assertNotNull(getLoansLoanIdCollectionData);
            assertEquals(0, getLoansLoanIdCollectionData.getDelinquentDays());
            assertEquals(0, getLoansLoanIdCollectionData.getPastDueDays());

        } finally {
            GlobalConfigurationHelper.updateIsBusinessDateEnabled(REQUEST_SPEC, RESPONSE_SPEC, Boolean.FALSE);
        }
    }

    @Test
    public void testDelinquencyWithPauseLettingPauseExpire() {
        runAt("01 January 2012", () -> {

            Integer delinquencyBucketId = DelinquencyBucketsHelper.createDelinquencyBucket(REQUEST_SPEC, RESPONSE_SPEC, List.of(//
                    Pair.of(1, 3), //
                    Pair.of(4, 60) //
            ));
            LocalDate bussinesLocalDate = Utils.getDateAsLocalDate("01 January 2012");

            final Integer clientId = ClientHelper.createClient(this.REQUEST_SPEC, this.RESPONSE_SPEC, "01 January 2012");
            final GetLoanProductsProductIdResponse getLoanProductsProductResponse = createLoanProduct(LOAN_TRANSACTION_HELPER,
                    delinquencyBucketId, "3");
            assertNotNull(getLoanProductsProductResponse);
            log.info("Loan Product Arrears: {}", getLoanProductsProductResponse.getInArrearsTolerance());
            assertEquals(3, getLoanProductsProductResponse.getInArrearsTolerance());

            final LocalDate transactionDate = bussinesLocalDate;
            String operationDate = Utils.dateFormatter.format(transactionDate);

            final Integer loanId = createLoanAccount(LOAN_TRANSACTION_HELPER, clientId.toString(),
                    getLoanProductsProductResponse.getId().toString(), operationDate, "3");

            GetLoansLoanIdResponse getLoansLoanIdResponse = LOAN_TRANSACTION_HELPER.getLoan(REQUEST_SPEC, RESPONSE_SPEC, loanId);
            final GetDelinquencyRangesResponse firstTestCase = getLoansLoanIdResponse.getDelinquencyRange();
            log.info("Loan Delinquency Range is null {}", (firstTestCase == null));
            LOAN_TRANSACTION_HELPER.printRepaymentSchedule(getLoansLoanIdResponse);
            log.info("Loan Account Arrears {}", getLoansLoanIdResponse.getInArrearsTolerance());
            assertEquals(3, getLoansLoanIdResponse.getInArrearsTolerance());

            final String jobName = "Loan COB";

            CobHelper.fastForwardLoansLastCOBDate(REQUEST_SPEC, RESPONSE_SPEC_204, loanId, "04 February 2012");
            updateBusinessDate("06 February 2012");
            SCHEDULER_JOB_HELPER.executeAndAwaitJob(jobName);
            verifyDelinquency(loanId, "01 February 2012", 1033.33, 5);

            PostLoansDelinquencyActionResponse pauseDelinquencyResponse = LOAN_TRANSACTION_HELPER
                    .createLoanDelinquencyAction(loanId.longValue(), PAUSE, "06 February 2012", "10 February 2012");

            CobHelper.fastForwardLoansLastCOBDate(REQUEST_SPEC, RESPONSE_SPEC_204, loanId, "07 February 2012");
            updateBusinessDate("09 February 2012");
            SCHEDULER_JOB_HELPER.executeAndAwaitJob(jobName);
            verifyDelinquency(loanId, "01 February 2012", 1033.33, 5);

            CobHelper.fastForwardLoansLastCOBDate(REQUEST_SPEC, RESPONSE_SPEC_204, loanId, "10 March 2012");
            updateBusinessDate("12 March 2012");
            SCHEDULER_JOB_HELPER.executeAndAwaitJob(jobName);
            verifyDelinquency(loanId, "01 February 2012", 2049.99, 36);
        });
    }

    @Test
    public void testDelinquencyWithPauseResumeBeforePauseExpires() {
        runAt("01 January 2012", () -> {
            Integer delinquencyBucketId = DelinquencyBucketsHelper.createDelinquencyBucket(REQUEST_SPEC, RESPONSE_SPEC, List.of(//
                    Pair.of(1, 3), //
                    Pair.of(4, 60) //
            ));
            LocalDate bussinesLocalDate = Utils.getDateAsLocalDate("01 January 2012");

            final Integer clientId = ClientHelper.createClient(this.REQUEST_SPEC, this.RESPONSE_SPEC, "01 January 2012");
            final GetLoanProductsProductIdResponse getLoanProductsProductResponse = createLoanProduct(LOAN_TRANSACTION_HELPER,
                    delinquencyBucketId, "3");
            assertNotNull(getLoanProductsProductResponse);
            log.info("Loan Product Arrears: {}", getLoanProductsProductResponse.getInArrearsTolerance());
            assertEquals(3, getLoanProductsProductResponse.getInArrearsTolerance());

            final LocalDate transactionDate = bussinesLocalDate;
            String operationDate = Utils.dateFormatter.format(transactionDate);

            final Integer loanId = createLoanAccount(LOAN_TRANSACTION_HELPER, clientId.toString(),
                    getLoanProductsProductResponse.getId().toString(), operationDate, "3");

            GetLoansLoanIdResponse getLoansLoanIdResponse = LOAN_TRANSACTION_HELPER.getLoan(REQUEST_SPEC, RESPONSE_SPEC, loanId);
            final GetDelinquencyRangesResponse firstTestCase = getLoansLoanIdResponse.getDelinquencyRange();
            log.info("Loan Delinquency Range is null {}", (firstTestCase == null));
            LOAN_TRANSACTION_HELPER.printRepaymentSchedule(getLoansLoanIdResponse);
            log.info("Loan Account Arrears {}", getLoansLoanIdResponse.getInArrearsTolerance());
            assertEquals(3, getLoansLoanIdResponse.getInArrearsTolerance());

            final String jobName = "Loan COB";

            CobHelper.fastForwardLoansLastCOBDate(REQUEST_SPEC, RESPONSE_SPEC_204, loanId, "04 February 2012");
            updateBusinessDate("06 February 2012");
            SCHEDULER_JOB_HELPER.executeAndAwaitJob(jobName);
            verifyDelinquency(loanId, "01 February 2012", 1033.33, 5);

            PostLoansDelinquencyActionResponse pauseDelinquencyResponse = LOAN_TRANSACTION_HELPER
                    .createLoanDelinquencyAction(loanId.longValue(), PAUSE, "06 February 2012", "10 March 2012");

            CobHelper.fastForwardLoansLastCOBDate(REQUEST_SPEC, RESPONSE_SPEC_204, loanId, "07 February 2012");
            updateBusinessDate("09 February 2012");
            SCHEDULER_JOB_HELPER.executeAndAwaitJob(jobName);
            verifyDelinquency(loanId, "01 February 2012", 1033.33, 5);

            bussinesLocalDate = Utils.getDateAsLocalDate("10 February 2012");
            BusinessDateHelper.updateBusinessDate(REQUEST_SPEC, RESPONSE_SPEC, BusinessDateType.BUSINESS_DATE, bussinesLocalDate);
            LOAN_TRANSACTION_HELPER.createLoanDelinquencyAction(loanId.longValue(), RESUME, "10 February 2012");

            CobHelper.fastForwardLoansLastCOBDate(REQUEST_SPEC, RESPONSE_SPEC_204, loanId, "10 March 2012");
            updateBusinessDate("12 March 2012");
            SCHEDULER_JOB_HELPER.executeAndAwaitJob(jobName);
            verifyDelinquency(loanId, "01 February 2012", 2049.99, 36);
        });
    }

    @Test
    public void testDelinquencyWithMultiplePausePeriods() {
        runAt("01 January 2012", () -> {

            Integer delinquencyBucketId = DelinquencyBucketsHelper.createDelinquencyBucket(REQUEST_SPEC, RESPONSE_SPEC, List.of(//
                    Pair.of(1, 3), //
                    Pair.of(4, 60) //
            ));

            final Integer clientId = ClientHelper.createClient(this.REQUEST_SPEC, this.RESPONSE_SPEC, "01 January 2012");
            final GetLoanProductsProductIdResponse getLoanProductsProductResponse = createLoanProduct(LOAN_TRANSACTION_HELPER,
                    delinquencyBucketId, "3");
            assertNotNull(getLoanProductsProductResponse);
            log.info("Loan Product Arrears: {}", getLoanProductsProductResponse.getInArrearsTolerance());
            assertEquals(3, getLoanProductsProductResponse.getInArrearsTolerance());

            final Integer loanId = createLoanAccount(LOAN_TRANSACTION_HELPER, clientId.toString(),
                    getLoanProductsProductResponse.getId().toString(), "01 January 2012", "3");

            GetLoansLoanIdResponse getLoansLoanIdResponse = LOAN_TRANSACTION_HELPER.getLoan(REQUEST_SPEC, RESPONSE_SPEC, loanId);
            final GetDelinquencyRangesResponse firstTestCase = getLoansLoanIdResponse.getDelinquencyRange();
            log.info("Loan Delinquency Range is null {}", (firstTestCase == null));
            LOAN_TRANSACTION_HELPER.printRepaymentSchedule(getLoansLoanIdResponse);
            log.info("Loan Account Arrears {}", getLoansLoanIdResponse.getInArrearsTolerance());
            assertEquals(3, getLoansLoanIdResponse.getInArrearsTolerance());

            final String jobName = "Loan COB";

            // delinquent days: 5
            CobHelper.fastForwardLoansLastCOBDate(REQUEST_SPEC, RESPONSE_SPEC_204, loanId, "04 February 2012");
            updateBusinessDate("06 February 2012");
            SCHEDULER_JOB_HELPER.executeAndAwaitJob(jobName);
            verifyDelinquency(loanId, "01 February 2012", 1033.33, 5);

            // Add delinquency pause on 06 February 2012
            PostLoansDelinquencyActionResponse pauseDelinquencyResponse = LOAN_TRANSACTION_HELPER
                    .createLoanDelinquencyAction(loanId.longValue(), PAUSE, "06 February 2012", "10 March 2012");
            CobHelper.fastForwardLoansLastCOBDate(REQUEST_SPEC, RESPONSE_SPEC_204, loanId, "07 February 2012");
            updateBusinessDate("09 February 2012");
            SCHEDULER_JOB_HELPER.executeAndAwaitJob(jobName);
            verifyDelinquency(loanId, "01 February 2012", 1033.33, 5);

            // Add delinquency resume on 10 February 2012
            updateBusinessDate("10 February 2012");
            LOAN_TRANSACTION_HELPER.createLoanDelinquencyAction(loanId.longValue(), RESUME, "10 February 2012");

            CobHelper.fastForwardLoansLastCOBDate(REQUEST_SPEC, RESPONSE_SPEC_204, loanId, "11 February 2012");
            updateBusinessDate("13 February 2012");
            SCHEDULER_JOB_HELPER.executeAndAwaitJob(jobName);
            verifyDelinquency(loanId, "01 February 2012", 1033.33, 8);

            // Add new pause on 13 February 2012
            pauseDelinquencyResponse = LOAN_TRANSACTION_HELPER.createLoanDelinquencyAction(loanId.longValue(), PAUSE, "13 February 2012",
                    "18 February 2012");

            CobHelper.fastForwardLoansLastCOBDate(REQUEST_SPEC, RESPONSE_SPEC_204, loanId, "21 February 2012");
            updateBusinessDate("23 February 2012");
            SCHEDULER_JOB_HELPER.executeAndAwaitJob(jobName);
            verifyDelinquency(loanId, "01 February 2012", 1033.33, 13);

            // Add new pause on 23 February 2012
            pauseDelinquencyResponse = LOAN_TRANSACTION_HELPER.createLoanDelinquencyAction(loanId.longValue(), PAUSE, "23 February 2012",
                    "28 February 2012");
            updateBusinessDate("25 February 2012");
            LOAN_TRANSACTION_HELPER.createLoanDelinquencyAction(loanId.longValue(), RESUME, "25 February 2012");
            CobHelper.fastForwardLoansLastCOBDate(REQUEST_SPEC, RESPONSE_SPEC_204, loanId, "10 March 2012");
            updateBusinessDate("12 March 2012");
            SCHEDULER_JOB_HELPER.executeAndAwaitJob(jobName);
            verifyDelinquency(loanId, "01 February 2012", 2049.99, 29);
        });
    }

    private void verifyDelinquency(Integer loanId, String date, Double amount, int delinquentDays) {
        GetLoansLoanIdResponse getLoansLoanIdResponse;
        GetLoansLoanIdDelinquencySummary delinquent;
        getLoansLoanIdResponse = LOAN_TRANSACTION_HELPER.getLoan(REQUEST_SPEC, RESPONSE_SPEC, loanId);
        LOAN_TRANSACTION_HELPER.printDelinquencyData(getLoansLoanIdResponse);
        delinquent = getLoansLoanIdResponse.getDelinquent();
        assertEquals(amount, delinquent.getDelinquentAmount());
        assertEquals(LocalDate.parse(date, DATE_FORMATTER), delinquent.getDelinquentDate());
        assertEquals(delinquentDays, delinquent.getDelinquentDays());
    }

    @Test
    public void testDelinquencyWithMultiplePausePeriodsWithInstallmentLevelDelinquency() {
        runAt("01 January 2012", () -> {
            Integer delinquencyBucketId = DelinquencyBucketsHelper.createDelinquencyBucket(REQUEST_SPEC, RESPONSE_SPEC, List.of(//
                    Pair.of(1, 3), //
                    Pair.of(4, 60) //
            ));

            final Integer clientId = ClientHelper.createClient(this.REQUEST_SPEC, this.RESPONSE_SPEC, "01 January 2012");
            final GetLoanProductsProductIdResponse getLoanProductsProductResponse = createLoanProductWithInstallmentLevelDelinquency(
                    LOAN_TRANSACTION_HELPER, delinquencyBucketId, "3");
            assertNotNull(getLoanProductsProductResponse);
            log.info("Loan Product Arrears: {}", getLoanProductsProductResponse.getInArrearsTolerance());
            assertEquals(3, getLoanProductsProductResponse.getInArrearsTolerance());

            final Integer loanId = createLoanAccount(LOAN_TRANSACTION_HELPER, clientId.toString(),
                    getLoanProductsProductResponse.getId().toString(), "01 January 2012", "3");

            GetLoansLoanIdResponse getLoansLoanIdResponse = LOAN_TRANSACTION_HELPER.getLoan(REQUEST_SPEC, RESPONSE_SPEC, loanId);
            final GetDelinquencyRangesResponse firstTestCase = getLoansLoanIdResponse.getDelinquencyRange();
            log.info("Loan Delinquency Range is null {}", (firstTestCase == null));
            LOAN_TRANSACTION_HELPER.printRepaymentSchedule(getLoansLoanIdResponse);
            log.info("Loan Account Arrears {}", getLoansLoanIdResponse.getInArrearsTolerance());
            assertEquals(3, getLoansLoanIdResponse.getInArrearsTolerance());

            final String jobName = "Loan COB";

            // delinquent days: 5
            CobHelper.fastForwardLoansLastCOBDate(REQUEST_SPEC, RESPONSE_SPEC_204, loanId, "04 February 2012");
            updateBusinessDate("06 February 2012");
            SCHEDULER_JOB_HELPER.executeAndAwaitJob(jobName);
            verifyDelinquency(loanId, "01 February 2012", 1033.33, 5);

            PostLoansDelinquencyActionResponse pauseDelinquencyResponse = LOAN_TRANSACTION_HELPER
                    .createLoanDelinquencyAction(loanId.longValue(), PAUSE, "06 February 2012", "10 March 2012");

            CobHelper.fastForwardLoansLastCOBDate(REQUEST_SPEC, RESPONSE_SPEC_204, loanId, "07 February 2012");
            updateBusinessDate("09 February 2012");
            SCHEDULER_JOB_HELPER.executeAndAwaitJob(jobName);
            verifyDelinquency(loanId, "01 February 2012", 1033.33, 5);

            updateBusinessDate("10 February 2012");
            LOAN_TRANSACTION_HELPER.createLoanDelinquencyAction(loanId.longValue(), RESUME, "10 February 2012");

            CobHelper.fastForwardLoansLastCOBDate(REQUEST_SPEC, RESPONSE_SPEC_204, loanId, "11 February 2012");
            updateBusinessDate("13 February 2012");
            SCHEDULER_JOB_HELPER.executeAndAwaitJob(jobName);
            verifyDelinquency(loanId, "01 February 2012", 1033.33, 8);

            pauseDelinquencyResponse = LOAN_TRANSACTION_HELPER.createLoanDelinquencyAction(loanId.longValue(), PAUSE, "13 February 2012",
                    "18 February 2012");

            CobHelper.fastForwardLoansLastCOBDate(REQUEST_SPEC, RESPONSE_SPEC_204, loanId, "21 February 2012");
            updateBusinessDate("23 February 2012");
            SCHEDULER_JOB_HELPER.executeAndAwaitJob(jobName);
            verifyDelinquency(loanId, "01 February 2012", 1033.33, 13);

            pauseDelinquencyResponse = LOAN_TRANSACTION_HELPER.createLoanDelinquencyAction(loanId.longValue(), PAUSE, "23 February 2012",
                    "28 February 2012");

            updateBusinessDate("25 February 2012");
            LOAN_TRANSACTION_HELPER.createLoanDelinquencyAction(loanId.longValue(), RESUME, "25 February 2012");

            CobHelper.fastForwardLoansLastCOBDate(REQUEST_SPEC, RESPONSE_SPEC_204, loanId, "12 March 2012");
            updateBusinessDate("14 March 2012");
            SCHEDULER_JOB_HELPER.executeAndAwaitJob(jobName);

            getLoansLoanIdResponse = LOAN_TRANSACTION_HELPER.getLoan(REQUEST_SPEC, RESPONSE_SPEC, loanId);
            LOAN_TRANSACTION_HELPER.printDelinquencyData(getLoansLoanIdResponse);
            GetLoansLoanIdDelinquencySummary delinquent = getLoansLoanIdResponse.getDelinquent();
            assertEquals(2049.99, delinquent.getDelinquentAmount());
            assertEquals(LocalDate.of(2012, 2, 1), delinquent.getDelinquentDate());
            assertEquals(31, delinquent.getDelinquentDays());
            assertEquals(2, delinquent.getInstallmentLevelDelinquency().size());
            GetLoansLoanIdLoanInstallmentLevelDelinquency firstInstallmentDelinquent = delinquent.getInstallmentLevelDelinquency().get(0);
            assertEquals(BigDecimal.valueOf(1016.66), firstInstallmentDelinquent.getDelinquentAmount().stripTrailingZeros());
            GetLoansLoanIdLoanInstallmentLevelDelinquency secondInstallmentDelinquent = delinquent.getInstallmentLevelDelinquency().get(1);
            assertEquals(BigDecimal.valueOf(1033.33), secondInstallmentDelinquent.getDelinquentAmount().stripTrailingZeros());
        });
    }

    @Test
    public void testLoanClassificationOnlyForActiveLoan() {

        // Given
        final LoanTransactionHelper loanTransactionHelper = new LoanTransactionHelper(this.REQUEST_SPEC, this.RESPONSE_SPEC);

        ArrayList<Integer> rangeIds = new ArrayList<>();
        // First Range
        String jsonRange = DelinquencyRangesHelper.getAsJSON(4, 30);
        PostDelinquencyRangeResponse delinquencyRangeResponse = DelinquencyRangesHelper.createDelinquencyRange(REQUEST_SPEC, RESPONSE_SPEC,
                jsonRange);
        rangeIds.add(delinquencyRangeResponse.getResourceId());

        String jsonBucket = DelinquencyBucketsHelper.getAsJSON(rangeIds);
        PostDelinquencyBucketResponse delinquencyBucketResponse = DelinquencyBucketsHelper.createDelinquencyBucket(REQUEST_SPEC,
                RESPONSE_SPEC, jsonBucket);
        assertNotNull(delinquencyBucketResponse);
        final GetDelinquencyBucketsResponse delinquencyBucket = DelinquencyBucketsHelper.getDelinquencyBucket(REQUEST_SPEC, RESPONSE_SPEC,
                delinquencyBucketResponse.getResourceId());

        // Client and Loan account creation
        final Integer clientId = ClientHelper.createClient(this.REQUEST_SPEC, this.RESPONSE_SPEC, "01 January 2012");
        final GetLoanProductsProductIdResponse getLoanProductsProductResponse = createLoanProduct(loanTransactionHelper,
                Math.toIntExact(delinquencyBucket.getId()), null);
        assertNotNull(getLoanProductsProductResponse);

        final LocalDate todaysDate = Utils.getLocalDateOfTenant();
        // Older date to have more than one overdue installment
        LocalDate transactionDate = todaysDate.minusDays(37);
        String operationDate = Utils.dateFormatter.format(transactionDate);

        // Create Loan Application
        final Integer loanId = createLoanApplication(loanTransactionHelper, clientId.toString(),
                getLoanProductsProductResponse.getId().toString(), operationDate, null);

        // Evaluate default delinquent values in No Active Loan
        GetLoansLoanIdResponse getLoansLoanIdResponse = loanTransactionHelper.getLoan(REQUEST_SPEC, RESPONSE_SPEC, loanId);
        assertNotNull(getLoansLoanIdResponse);
        assertNotNull(getLoansLoanIdResponse.getDelinquent());
        assertEquals(0, getLoansLoanIdResponse.getDelinquent().getDelinquentDays());
        assertEquals(0, getLoansLoanIdResponse.getDelinquent().getDelinquentAmount());

        // Loan Disbursement
        disburseLoanAccount(loanTransactionHelper, loanId, operationDate);
        // Evaluate default delinquent values in No Active Loan
        getLoansLoanIdResponse = loanTransactionHelper.getLoan(REQUEST_SPEC, RESPONSE_SPEC, loanId);
        assertNotNull(getLoansLoanIdResponse);
        assertNotNull(getLoansLoanIdResponse.getDelinquent());
        assertNotEquals(0, getLoansLoanIdResponse.getDelinquent().getDelinquentDays());
        assertNotEquals(0, getLoansLoanIdResponse.getDelinquent().getDelinquentAmount());
    }

    @Test
    public void testLoanClassificationOnlyForActiveLoanWithCOB() {
        try {
            GlobalConfigurationHelper.updateIsBusinessDateEnabled(REQUEST_SPEC, RESPONSE_SPEC, Boolean.TRUE);
            final String operationDate = "01 January 2012";

            LocalDate bussinesLocalDate = Utils.getDateAsLocalDate(operationDate);
            log.info("Current date {}", bussinesLocalDate);
            BusinessDateHelper.updateBusinessDate(REQUEST_SPEC, RESPONSE_SPEC, BusinessDateType.BUSINESS_DATE, bussinesLocalDate);

            // Given
            final LoanTransactionHelper loanTransactionHelper = new LoanTransactionHelper(this.REQUEST_SPEC, this.RESPONSE_SPEC);
            final SchedulerJobHelper schedulerJobHelper = new SchedulerJobHelper(REQUEST_SPEC);

            ArrayList<Integer> rangeIds = new ArrayList<>();
            // First Range
            String jsonRange = DelinquencyRangesHelper.getAsJSON(4, 30);
            PostDelinquencyRangeResponse delinquencyRangeResponse = DelinquencyRangesHelper.createDelinquencyRange(REQUEST_SPEC,
                    RESPONSE_SPEC, jsonRange);
            rangeIds.add(delinquencyRangeResponse.getResourceId());

            String jsonBucket = DelinquencyBucketsHelper.getAsJSON(rangeIds);
            PostDelinquencyBucketResponse delinquencyBucketResponse = DelinquencyBucketsHelper.createDelinquencyBucket(REQUEST_SPEC,
                    RESPONSE_SPEC, jsonBucket);
            assertNotNull(delinquencyBucketResponse);
            final GetDelinquencyBucketsResponse delinquencyBucket = DelinquencyBucketsHelper.getDelinquencyBucket(REQUEST_SPEC,
                    RESPONSE_SPEC, delinquencyBucketResponse.getResourceId());

            // Client creation
            final Integer clientId = ClientHelper.createClient(this.REQUEST_SPEC, this.RESPONSE_SPEC, operationDate);
            final GetLoanProductsProductIdResponse getLoanProductsProductResponse = createLoanProduct(loanTransactionHelper,
                    Math.toIntExact(delinquencyBucket.getId()), null);
            assertNotNull(getLoanProductsProductResponse);

            // Create Loan Application
            final Integer loanId = createLoanApplication(loanTransactionHelper, clientId.toString(),
                    getLoanProductsProductResponse.getId().toString(), operationDate, null);

            // run cob for business date 01 January 2012
            final String jobName = "Loan COB";
            bussinesLocalDate = Utils.getDateAsLocalDate(operationDate);
            BusinessDateHelper.updateBusinessDate(REQUEST_SPEC, RESPONSE_SPEC, BusinessDateType.BUSINESS_DATE, bussinesLocalDate);
            schedulerJobHelper.executeAndAwaitJob(jobName);

            // Loan delinquency data
            GetLoansLoanIdResponse getLoansLoanIdResponse = loanTransactionHelper.getLoan(REQUEST_SPEC, RESPONSE_SPEC, loanId);
            loanTransactionHelper.printDelinquencyData(getLoansLoanIdResponse);
            GetLoansLoanIdDelinquencySummary delinquent = getLoansLoanIdResponse.getDelinquent();
            assertNotNull(getLoansLoanIdResponse);
            assertNotNull(delinquent);
            assertEquals(0, delinquent.getDelinquentDays());
            assertEquals(0, delinquent.getDelinquentAmount());

        } finally {
            GlobalConfigurationHelper.updateIsBusinessDateEnabled(REQUEST_SPEC, RESPONSE_SPEC, Boolean.FALSE);
        }
    }

    private GetLoanProductsProductIdResponse createLoanProduct(final LoanTransactionHelper loanTransactionHelper,
            final Integer delinquencyBucketId, final String inArrearsTolerance) {
        final HashMap<String, Object> loanProductMap = new LoanProductTestBuilder().withInArrearsTolerance(inArrearsTolerance).build(null,
                delinquencyBucketId);
        final Integer loanProductId = loanTransactionHelper.getLoanProductId(Utils.convertToJson(loanProductMap));
        return loanTransactionHelper.getLoanProduct(loanProductId);
    }

    private GetLoanProductsProductIdResponse createLoanProductWithInstallmentLevelDelinquency(
            final LoanTransactionHelper loanTransactionHelper, final Integer delinquencyBucketId, final String inArrearsTolerance) {
        final HashMap<String, Object> loanProductMap = new LoanProductTestBuilder().withInArrearsTolerance(inArrearsTolerance).build(null,
                delinquencyBucketId);
        loanProductMap.put("enableInstallmentLevelDelinquency", true);
        final Integer loanProductId = loanTransactionHelper.getLoanProductId(Utils.convertToJson(loanProductMap));
        return loanTransactionHelper.getLoanProduct(loanProductId);
    }

    private PutLoanProductsProductIdResponse updateLoanProduct(LoanTransactionHelper loanTransactionHelper, Long id,
            final Integer inArrearsTolerance) {
        final PutLoanProductsProductIdRequest requestModifyLoan = new PutLoanProductsProductIdRequest()
                .inArrearsTolerance(inArrearsTolerance);
        return loanTransactionHelper.updateLoanProduct(id, requestModifyLoan);
    }

    private Integer createLoanApplication(final LoanTransactionHelper loanTransactionHelper, final String clientId,
            final String loanProductId, final String operationDate, final String inArrearsTolerance) {
        final String loanApplicationJSON = new LoanApplicationTestBuilder().withPrincipal(principalAmount).withLoanTermFrequency("12")
                .withLoanTermFrequencyAsMonths().withNumberOfRepayments("12").withRepaymentEveryAfter("1")
                .withRepaymentFrequencyTypeAsMonths() //
                .withInterestRatePerPeriod("2") //
                .withExpectedDisbursementDate(operationDate) //
                .withInterestTypeAsDecliningBalance() //
                .withSubmittedOnDate(operationDate) //
                .withInArrearsTolerance(inArrearsTolerance) //
                .build(clientId, loanProductId, null);
        final Integer loanId = loanTransactionHelper.getLoanId(loanApplicationJSON);
        loanTransactionHelper.approveLoan(operationDate, principalAmount, loanId, null);
        return loanId;
    }

    private void disburseLoanAccount(final LoanTransactionHelper loanTransactionHelper, final Integer loanId, final String operationDate) {
        loanTransactionHelper.disburseLoanWithNetDisbursalAmount(operationDate, loanId, principalAmount);
    }

    private Integer createLoanAccount(final LoanTransactionHelper loanTransactionHelper, final String clientId, final String loanProductId,
            final String operationDate, final String inArrearsTolerance) {
        final Integer loanId = createLoanApplication(loanTransactionHelper, clientId, loanProductId, operationDate, inArrearsTolerance);
        disburseLoanAccount(loanTransactionHelper, loanId, operationDate);
        return loanId;
    }

    private String getChargeApplyJSON(final Integer chargeId, final String dueDate) {
        final HashMap<String, Object> map = new HashMap<>();
        map.put("chargeId", chargeId);
        map.put("amount", 12.0f);
        map.put("dueDate", dueDate);
        map.put("dateFormat", Utils.DATE_FORMAT);
        map.put("locale", CommonConstants.LOCALE);
        final String chargeApplyJSON = new Gson().toJson(map);
        log.info("{}", chargeApplyJSON);
        return chargeApplyJSON;
    }
}
