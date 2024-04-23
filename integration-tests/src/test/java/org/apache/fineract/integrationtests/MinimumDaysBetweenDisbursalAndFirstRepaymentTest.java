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

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.apache.fineract.integrationtests.common.CalendarHelper;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.CollateralManagementHelper;
import org.apache.fineract.integrationtests.common.CommonConstants;
import org.apache.fineract.integrationtests.common.GroupHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.loans.LoanApplicationTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanProductTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanStatusChecker;
import org.apache.fineract.integrationtests.common.loans.LoanTestLifecycleExtension;
import org.apache.fineract.integrationtests.common.loans.LoanTransactionHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Test the creation, approval and rejection of a loan reschedule request
 **/
@SuppressWarnings({ "rawtypes" })
@ExtendWith(LoanTestLifecycleExtension.class)
public class MinimumDaysBetweenDisbursalAndFirstRepaymentTest {

    private ResponseSpecification responseSpec;
    private ResponseSpecification responseSpecForStatusCode403;
    private RequestSpecification requestSpec;
    private LoanTransactionHelper loanTransactionHelper;
    private Integer clientId;
    private Integer groupId;
    private Integer groupCalendarId;
    private Integer loanProductId;
    private Integer loanId;
    private final String loanPrincipalAmount = "100000.00";
    private final String numberOfRepayments = "12";
    private final String interestRatePerPeriod = "18";
    private final String groupActivationDate = "01 August 2014";

    @BeforeEach
    public void setup() {
        Utils.initializeRESTAssured();
    }

    /*
     * MinimumDaysBetweenDisbursalAndFirstRepayment is set to 7 days and days between disbursal date and first repayment
     * is set as 7. system should allow to create this loan and allow to disburse
     */
    @Test
    public void createLoanEntity_WITH_DAY_BETWEEN_DISB_DATE_AND_REPAY_START_DATE_GREATER_THAN_MIN_DAY_CRITERIA() {

        requestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        requestSpec.header("Authorization", "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey());
        responseSpec = new ResponseSpecBuilder().expectStatusCode(200).build();
        loanTransactionHelper = new LoanTransactionHelper(requestSpec, responseSpec);

        // create all required entities
        this.createRequiredEntities();

        final String disbursalDate = "04 September 2014";
        final String firstRepaymentDate = "11 September 2014";

        List<HashMap> collaterals = new ArrayList<>();
        final Integer collateralId = CollateralManagementHelper.createCollateralProduct(requestSpec, responseSpec);
        Assertions.assertNotNull(collateralId);
        final Integer clientCollateralId = CollateralManagementHelper.createClientCollateral(requestSpec, responseSpec,
                this.clientId.toString(), collateralId);
        Assertions.assertNotNull(clientCollateralId);
        addCollaterals(collaterals, clientCollateralId, BigDecimal.valueOf(1));

        final String loanApplicationJSON = new LoanApplicationTestBuilder().withPrincipal(loanPrincipalAmount)
                .withLoanTermFrequency(numberOfRepayments).withLoanTermFrequencyAsWeeks().withNumberOfRepayments(numberOfRepayments)
                .withRepaymentEveryAfter("1").withRepaymentFrequencyTypeAsMonths().withAmortizationTypeAsEqualInstallments()
                .withInterestCalculationPeriodTypeAsDays().withInterestRatePerPeriod(interestRatePerPeriod)
                .withRepaymentFrequencyTypeAsWeeks().withSubmittedOnDate(disbursalDate).withExpectedDisbursementDate(disbursalDate)
                .withPrincipalGrace("2").withInterestGrace("2").withFirstRepaymentDate(firstRepaymentDate).withCollaterals(collaterals)
                .build(this.clientId.toString(), this.loanProductId.toString(), null);

        this.loanId = loanTransactionHelper.getLoanId(loanApplicationJSON);

        // Test for loan account is created
        Assertions.assertNotNull(this.loanId);
        HashMap loanStatusHashMap = LoanStatusChecker.getStatusOfLoan(requestSpec, responseSpec, this.loanId);
        LoanStatusChecker.verifyLoanIsPending(loanStatusHashMap);

        // Test for loan account is created, can be approved
        loanTransactionHelper.approveLoan(disbursalDate, this.loanId);
        loanStatusHashMap = LoanStatusChecker.getStatusOfLoan(requestSpec, responseSpec, this.loanId);
        LoanStatusChecker.verifyLoanIsApproved(loanStatusHashMap);

        // Test for loan account approved can be disbursed
        String loanDetails = loanTransactionHelper.getLoanDetails(requestSpec, responseSpec, this.loanId);
        loanTransactionHelper.disburseLoanWithNetDisbursalAmount(disbursalDate, this.loanId,
                JsonPath.from(loanDetails).get("netDisbursalAmount").toString());
        loanStatusHashMap = LoanStatusChecker.getStatusOfLoan(requestSpec, responseSpec, this.loanId);
        LoanStatusChecker.verifyLoanIsActive(loanStatusHashMap);

    }

    private void addCollaterals(List<HashMap> collaterals, Integer collateralId, BigDecimal quantity) {
        collaterals.add(collaterals(collateralId, quantity));
    }

    private HashMap<String, String> collaterals(Integer collateralId, BigDecimal quantity) {
        HashMap<String, String> collateral = new HashMap<String, String>(2);
        collateral.put("clientCollateralId", collateralId.toString());
        collateral.put("quantity", quantity.toString());
        return collateral;
    }

    /*
     * MinimumDaysBetweenDisbursalAndFirstRepayment is set to 7 days and days between disbursal date and first repayment
     * is set as 7. system should allow to create this loan and allow to disburse
     */
    @SuppressWarnings("unchecked")
    @Test
    public void createLoanEntity_WITH_DAY_BETWEEN_DISB_DATE_AND_REPAY_START_DATE_LESS_THAN_MIN_DAY_CRITERIA() {

        requestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        requestSpec.header("Authorization", "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey());
        responseSpec = new ResponseSpecBuilder().expectStatusCode(200).build();
        responseSpecForStatusCode403 = new ResponseSpecBuilder().expectStatusCode(403).build();
        loanTransactionHelper = new LoanTransactionHelper(requestSpec, responseSpec);
        // create all required entities
        this.createRequiredEntities();

        // loanTransactionHelper is reassigned to accept 403 status code from
        // server
        loanTransactionHelper = new LoanTransactionHelper(requestSpec, responseSpecForStatusCode403);

        final String disbursalDate = "04 September 2014";
        final String firstRepaymentDate = "05 September 2014";

        List<HashMap> collaterals = new ArrayList<>();
        final Integer collateralId = CollateralManagementHelper.createCollateralProduct(requestSpec, responseSpec);
        Assertions.assertNotNull(collateralId);
        final Integer clientCollateralId = CollateralManagementHelper.createClientCollateral(requestSpec, responseSpec,
                this.clientId.toString(), collateralId);
        Assertions.assertNotNull(clientCollateralId);
        addCollaterals(collaterals, clientCollateralId, BigDecimal.valueOf(1));

        final String loanApplicationJSON = new LoanApplicationTestBuilder().withPrincipal(loanPrincipalAmount)
                .withLoanTermFrequency(numberOfRepayments).withLoanTermFrequencyAsWeeks().withNumberOfRepayments(numberOfRepayments)
                .withRepaymentEveryAfter("1").withRepaymentFrequencyTypeAsMonths().withAmortizationTypeAsEqualInstallments()
                .withInterestCalculationPeriodTypeAsDays().withInterestRatePerPeriod(interestRatePerPeriod)
                .withRepaymentFrequencyTypeAsWeeks().withSubmittedOnDate(disbursalDate).withExpectedDisbursementDate(disbursalDate)
                .withPrincipalGrace("2").withInterestGrace("2").withFirstRepaymentDate(firstRepaymentDate).withCollaterals(collaterals)
                .build(this.clientId.toString(), this.loanProductId.toString(), null);

        List<HashMap> error = (List<HashMap>) loanTransactionHelper.createLoanAccount(loanApplicationJSON, CommonConstants.RESPONSE_ERROR);
        assertEquals("error.msg.loan.days.between.first.repayment.and.disbursal.are.less.than.minimum.allowed",
                error.get(0).get(CommonConstants.RESPONSE_ERROR_MESSAGE_CODE));

    }

    /**
     * Creates the client, loan product, and loan entities
     **/
    private void createRequiredEntities() {
        final String minimumDaysBetweenDisbursalAndFirstRepayment = "7"; // &
                                                                         // days
        this.createGroupEntityWithCalendar();
        this.createClientEntity();
        this.associateClientToGroup(this.groupId, this.clientId);
        this.createLoanProductEntity(minimumDaysBetweenDisbursalAndFirstRepayment);

    }

    /*
     * Associate client to the group
     */

    private void associateClientToGroup(final Integer groupId, final Integer clientId) {
        GroupHelper.associateClient(requestSpec, responseSpec, groupId.toString(), clientId.toString());
        GroupHelper.verifyGroupMembers(requestSpec, responseSpec, groupId, clientId);
    }

    /*
     * Create a new group
     */

    private void createGroupEntityWithCalendar() {
        this.groupId = GroupHelper.createGroup(requestSpec, responseSpec, this.groupActivationDate);
        GroupHelper.verifyGroupCreatedOnServer(requestSpec, responseSpec, this.groupId);

        final String startDate = this.groupActivationDate;
        final String frequency = "2"; // 2:Weekly
        final String interval = "1"; // Every one week
        final String repeatsOnDay = "1"; // 1:Monday

        this.setGroupCalendarId(CalendarHelper.createMeetingCalendarForGroup(requestSpec, responseSpec, this.groupId, startDate, frequency,
                interval, repeatsOnDay));
    }

    /**
     * create a new client
     **/
    private void createClientEntity() {
        this.clientId = ClientHelper.createClient(requestSpec, responseSpec);
        ClientHelper.verifyClientCreatedOnServer(requestSpec, responseSpec, this.clientId);
    }

    /**
     * create a new loan product
     **/
    private void createLoanProductEntity(final String minimumDaysBetweenDisbursalAndFirstRepayment) {
        final String loanProductJSON = new LoanProductTestBuilder().withPrincipal(loanPrincipalAmount)
                .withNumberOfRepayments(numberOfRepayments).withinterestRatePerPeriod(interestRatePerPeriod)
                .withInterestRateFrequencyTypeAsYear()
                .withMinimumDaysBetweenDisbursalAndFirstRepayment(minimumDaysBetweenDisbursalAndFirstRepayment).build(null);
        this.loanProductId = loanTransactionHelper.getLoanProductId(loanProductJSON);
    }

    public Integer getGroupCalendarId() {
        return groupCalendarId;
    }

    public void setGroupCalendarId(Integer groupCalendarId) {
        this.groupCalendarId = groupCalendarId;
    }
}
