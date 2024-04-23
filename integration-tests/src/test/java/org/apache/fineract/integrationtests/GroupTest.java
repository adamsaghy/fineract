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
import static org.junit.jupiter.api.Assertions.assertNotEquals;

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
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.CollateralManagementHelper;
import org.apache.fineract.integrationtests.common.GroupHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.loans.LoanApplicationTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanProductTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanTestLifecycleExtension;
import org.apache.fineract.integrationtests.common.loans.LoanTransactionHelper;
import org.apache.fineract.integrationtests.common.organisation.StaffHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Group Test for checking Group: Creation, Activation, Client Association, Updating & Deletion
 */
@ExtendWith(LoanTestLifecycleExtension.class)
public class GroupTest {

    private static final Logger LOG = LoggerFactory.getLogger(GroupTest.class);
    private ResponseSpecification responseSpec;
    private RequestSpecification requestSpec;
    private LoanTransactionHelper loanTransactionHelper;
    private final String principal = "10000.00";
    private final String accountingRule = "1";
    private final String numberOfRepayments = "5";
    private final String interestRatePerPeriod = "18";

    @BeforeEach
    public void setup() {
        Utils.initializeRESTAssured();
        requestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        requestSpec.header("Authorization", "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey());
        responseSpec = new ResponseSpecBuilder().expectStatusCode(200).build();
        loanTransactionHelper = new LoanTransactionHelper(requestSpec, responseSpec);
    }

    @Test
    public void checkGroupFunctions() {
        final Integer clientID = ClientHelper.createClient(requestSpec, responseSpec);
        Integer groupID = GroupHelper.createGroup(requestSpec, responseSpec);
        GroupHelper.verifyGroupCreatedOnServer(requestSpec, responseSpec, groupID);

        groupID = GroupHelper.activateGroup(requestSpec, responseSpec, groupID.toString());
        GroupHelper.verifyGroupActivatedOnServer(requestSpec, responseSpec, groupID, true);

        groupID = GroupHelper.associateClient(requestSpec, responseSpec, groupID.toString(), clientID.toString());
        GroupHelper.verifyGroupMembers(requestSpec, responseSpec, groupID, clientID);

        groupID = GroupHelper.disAssociateClient(requestSpec, responseSpec, groupID.toString(), clientID.toString());
        GroupHelper.verifyEmptyGroupMembers(requestSpec, responseSpec, groupID);

        final String updatedGroupName = GroupHelper.randomNameGenerator("Group-", 5);
        groupID = GroupHelper.updateGroup(requestSpec, responseSpec, updatedGroupName, groupID.toString());
        GroupHelper.verifyGroupDetails(requestSpec, responseSpec, groupID, "name", updatedGroupName);

        // NOTE: removed as consistently provides false positive result on
        // cloudbees server.
        // groupID = GroupHelper.createGroup(requestSpec,
        // responseSpec);
        // GroupHelper.deleteGroup(requestSpec, responseSpec,
        // groupID.toString());
        // GroupHelper.verifyGroupDeleted(requestSpec, responseSpec,
        // groupID);
    }

    @Test
    public void assignStaffToGroup() {
        Integer groupID = GroupHelper.createGroup(requestSpec, responseSpec);
        GroupHelper.verifyGroupCreatedOnServer(requestSpec, responseSpec, groupID);

        final String updateGroupName = Utils.uniqueRandomStringGenerator("Savings Group Help_", 5);
        groupID = GroupHelper.activateGroup(requestSpec, responseSpec, groupID.toString());
        Integer updateGroupId = GroupHelper.updateGroup(requestSpec, responseSpec, updateGroupName, groupID.toString());

        // create client and add client to group
        final Integer clientID = ClientHelper.createClient(requestSpec, responseSpec);
        ClientHelper.verifyClientCreatedOnServer(requestSpec, responseSpec, clientID);

        groupID = GroupHelper.associateClient(requestSpec, responseSpec, groupID.toString(), clientID.toString());
        GroupHelper.verifyGroupMembers(requestSpec, responseSpec, groupID, clientID);

        // create staff
        Integer createStaffId1 = StaffHelper.createStaff(requestSpec, responseSpec);
        LOG.info("--------------creating first staff with id------------- {}", createStaffId1);
        Assertions.assertNotNull(createStaffId1);

        Integer createStaffId2 = StaffHelper.createStaff(requestSpec, responseSpec);
        LOG.info("--------------creating second staff with id------------- {}", createStaffId2);
        Assertions.assertNotNull(createStaffId2);

        // assign staff "createStaffId1" to group
        HashMap assignStaffGroupId = (HashMap) GroupHelper.assignStaff(requestSpec, responseSpec, groupID.toString(),
                createStaffId1.longValue());
        assertEquals(assignStaffGroupId.get("staffId"), createStaffId1, "Verify assigned staff id is the same as id sent");

        // assign staff "createStaffId2" to client
        final HashMap assignStaffToClientChanges = (HashMap) ClientHelper.assignStaffToClient(requestSpec, responseSpec,
                clientID.toString(), createStaffId2.toString());
        assertEquals(assignStaffToClientChanges.get("staffId"), createStaffId2, "Verify assigned staff id is the same as id sent");

        final Integer loanProductId = this.createLoanProduct();

        final Integer loanId = this.applyForLoanApplication(clientID, loanProductId, this.principal);

        loanTransactionHelper.approveLoan("20 September 2014", loanId);
        String loanDetails = loanTransactionHelper.getLoanDetails(requestSpec, responseSpec, loanId);
        loanTransactionHelper.disburseLoanWithNetDisbursalAmount("20 September 2014", loanId,
                JsonPath.from(loanDetails).get("netDisbursalAmount").toString());

        final HashMap assignStaffAndInheritStaffForClientAccounts = (HashMap) GroupHelper
                .assignStaffInheritStaffForClientAccounts(requestSpec, responseSpec, groupID.toString(), createStaffId1.toString());
        final Integer getClientStaffId = ClientHelper.getClientsStaffId(requestSpec, responseSpec, clientID.toString());

        // assert if client staff officer has change Note client was assigned
        // staff with createStaffId2
        assertNotEquals(assignStaffAndInheritStaffForClientAccounts.get("staffId"), createStaffId2, "Verify if client stuff has changed");
        assertEquals(assignStaffAndInheritStaffForClientAccounts.get("staffId"), getClientStaffId,
                "Verify if client inherited staff assigned above");

        // assert if clients loan officer has changed
        final Integer loanOfficerId = loanTransactionHelper.getLoanOfficerId(loanId.toString());
        assertEquals(assignStaffAndInheritStaffForClientAccounts.get("staffId"), loanOfficerId, "Verify if client loan inherited staff");

    }

    private Integer createLoanProduct() {
        final String loanProductJSON = new LoanProductTestBuilder().withPrincipal(this.principal)
                .withNumberOfRepayments(this.numberOfRepayments).withinterestRatePerPeriod(this.interestRatePerPeriod)
                .withInterestRateFrequencyTypeAsYear().build(null);
        return loanTransactionHelper.getLoanProductId(loanProductJSON);
    }

    private Integer applyForLoanApplication(final Integer clientID, final Integer loanProductID, String principal) {
        LOG.info("--------------------------------APPLYING FOR LOAN APPLICATION--------------------------------");
        List<HashMap> collaterals = new ArrayList<>();
        final Integer collateralId = CollateralManagementHelper.createCollateralProduct(requestSpec, responseSpec);
        Assertions.assertNotNull(collateralId);
        final Integer clientCollateralId = CollateralManagementHelper.createClientCollateral(requestSpec, responseSpec,
                String.valueOf(clientID), collateralId);
        Assertions.assertNotNull(clientCollateralId);
        addCollaterals(collaterals, clientCollateralId, BigDecimal.valueOf(1));

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
                .withExpectedDisbursementDate("20 September 2014") //
                .withSubmittedOnDate("20 September 2014") //
                .withCollaterals(collaterals).build(clientID.toString(), loanProductID.toString(), null);
        return loanTransactionHelper.getLoanId(loanApplicationJSON);
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

}
