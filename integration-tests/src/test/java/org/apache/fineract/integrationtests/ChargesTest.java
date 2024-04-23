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

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import java.util.ArrayList;
import java.util.HashMap;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.charges.ChargesHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings({ "rawtypes" })
public class ChargesTest {

    private ResponseSpecification responseSpec;
    private RequestSpecification requestSpec;

    @BeforeEach
    public void setup() {
        Utils.initializeRESTAssured();
        requestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        requestSpec.header("Authorization", "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey());
        responseSpec = new ResponseSpecBuilder().expectStatusCode(200).build();
    }

    @Test
    public void testChargesForLoans() {

        // Retrieving all Charges
        ArrayList<HashMap> allChargesData = ChargesHelper.getCharges(requestSpec, responseSpec);
        Assertions.assertNotNull(allChargesData);

        // Testing Creation, Updation and Deletion of Disbursement Charge
        final Integer disbursementChargeId = ChargesHelper.createCharges(requestSpec, responseSpec,
                ChargesHelper.getLoanDisbursementJSON());
        Assertions.assertNotNull(disbursementChargeId);

        // Updating Charge Amount
        HashMap changes = ChargesHelper.updateCharges(requestSpec, responseSpec, disbursementChargeId, ChargesHelper.getModifyChargeJSON());

        HashMap chargeDataAfterChanges = ChargesHelper.getChargeById(requestSpec, responseSpec, disbursementChargeId);
        Assertions.assertEquals(chargeDataAfterChanges.get("amount"), changes.get("amount"), "Verifying Charge after Modification");

        changes = ChargesHelper.updateCharges(requestSpec, responseSpec, disbursementChargeId,
                ChargesHelper.getModifyChargeAsPecentageAmountJSON());

        chargeDataAfterChanges = ChargesHelper.getChargeById(requestSpec, responseSpec, disbursementChargeId);

        HashMap chargeChangedData = (HashMap) chargeDataAfterChanges.get("chargePaymentMode");
        Assertions.assertEquals(chargeChangedData.get("id"), changes.get("chargePaymentMode"), "Verifying Charge after Modification");

        chargeChangedData = (HashMap) chargeDataAfterChanges.get("chargeCalculationType");
        Assertions.assertEquals(chargeChangedData.get("id"), changes.get("chargeCalculationType"), "Verifying Charge after Modification");

        changes = ChargesHelper.updateCharges(requestSpec, responseSpec, disbursementChargeId,
                ChargesHelper.getModifyChargeAsPecentageLoanAmountWithInterestJSON());

        chargeDataAfterChanges = ChargesHelper.getChargeById(requestSpec, responseSpec, disbursementChargeId);

        chargeChangedData = (HashMap) chargeDataAfterChanges.get("chargeCalculationType");
        Assertions.assertEquals(chargeChangedData.get("id"), changes.get("chargeCalculationType"), "Verifying Charge after Modification");

        changes = ChargesHelper.updateCharges(requestSpec, responseSpec, disbursementChargeId,
                ChargesHelper.getModifyChargeAsPercentageInterestJSON());

        chargeDataAfterChanges = ChargesHelper.getChargeById(requestSpec, responseSpec, disbursementChargeId);

        chargeChangedData = (HashMap) chargeDataAfterChanges.get("chargeCalculationType");
        Assertions.assertEquals(chargeChangedData.get("id"), changes.get("chargeCalculationType"), "Verifying Charge after Modification");

        Integer chargeIdAfterDeletion = ChargesHelper.deleteCharge(responseSpec, requestSpec, disbursementChargeId);
        Assertions.assertEquals(disbursementChargeId, chargeIdAfterDeletion, "Verifying Charge ID after deletion");

        // Testing Creation, Updation and Deletion of Specified due date Charge
        final Integer specifiedDueDateChargeId = ChargesHelper.createCharges(requestSpec, responseSpec,
                ChargesHelper.getLoanSpecifiedDueDateJSON());
        Assertions.assertNotNull(specifiedDueDateChargeId);

        // Updating Charge Amount
        changes = ChargesHelper.updateCharges(requestSpec, responseSpec, specifiedDueDateChargeId, ChargesHelper.getModifyChargeJSON());

        chargeDataAfterChanges = ChargesHelper.getChargeById(requestSpec, responseSpec, specifiedDueDateChargeId);
        Assertions.assertEquals(chargeDataAfterChanges.get("amount"), changes.get("amount"), "Verifying Charge after Modification");

        changes = ChargesHelper.updateCharges(requestSpec, responseSpec, specifiedDueDateChargeId,
                ChargesHelper.getModifyChargeAsPecentageAmountJSON());

        chargeDataAfterChanges = ChargesHelper.getChargeById(requestSpec, responseSpec, specifiedDueDateChargeId);

        chargeChangedData = (HashMap) chargeDataAfterChanges.get("chargePaymentMode");
        Assertions.assertEquals(chargeChangedData.get("id"), changes.get("chargePaymentMode"), "Verifying Charge after Modification");

        chargeChangedData = (HashMap) chargeDataAfterChanges.get("chargeCalculationType");
        Assertions.assertEquals(chargeChangedData.get("id"), changes.get("chargeCalculationType"), "Verifying Charge after Modification");

        changes = ChargesHelper.updateCharges(requestSpec, responseSpec, specifiedDueDateChargeId,
                ChargesHelper.getModifyChargeAsPecentageLoanAmountWithInterestJSON());

        chargeDataAfterChanges = ChargesHelper.getChargeById(requestSpec, responseSpec, specifiedDueDateChargeId);

        chargeChangedData = (HashMap) chargeDataAfterChanges.get("chargeCalculationType");
        Assertions.assertEquals(chargeChangedData.get("id"), changes.get("chargeCalculationType"), "Verifying Charge after Modification");

        changes = ChargesHelper.updateCharges(requestSpec, responseSpec, specifiedDueDateChargeId,
                ChargesHelper.getModifyChargeAsPercentageInterestJSON());

        chargeDataAfterChanges = ChargesHelper.getChargeById(requestSpec, responseSpec, specifiedDueDateChargeId);

        chargeChangedData = (HashMap) chargeDataAfterChanges.get("chargeCalculationType");
        Assertions.assertEquals(chargeChangedData.get("id"), changes.get("chargeCalculationType"), "Verifying Charge after Modification");

        chargeIdAfterDeletion = ChargesHelper.deleteCharge(responseSpec, requestSpec, specifiedDueDateChargeId);
        Assertions.assertEquals(specifiedDueDateChargeId, chargeIdAfterDeletion, "Verifying Charge ID after deletion");

        // Testing Creation, Updation and Deletion of Installment Fee Charge
        final Integer installmentFeeChargeId = ChargesHelper.createCharges(requestSpec, responseSpec,
                ChargesHelper.getLoanInstallmentFeeJSON());

        // Updating Charge Amount
        changes = ChargesHelper.updateCharges(requestSpec, responseSpec, installmentFeeChargeId, ChargesHelper.getModifyChargeJSON());

        chargeDataAfterChanges = ChargesHelper.getChargeById(requestSpec, responseSpec, installmentFeeChargeId);
        Assertions.assertEquals(chargeDataAfterChanges.get("amount"), changes.get("amount"), "Verifying Charge after Modification");

        changes = ChargesHelper.updateCharges(requestSpec, responseSpec, installmentFeeChargeId,
                ChargesHelper.getModifyChargeAsPecentageAmountJSON());

        chargeDataAfterChanges = ChargesHelper.getChargeById(requestSpec, responseSpec, installmentFeeChargeId);

        chargeChangedData = (HashMap) chargeDataAfterChanges.get("chargePaymentMode");
        Assertions.assertEquals(chargeChangedData.get("id"), changes.get("chargePaymentMode"), "Verifying Charge after Modification");

        chargeChangedData = (HashMap) chargeDataAfterChanges.get("chargeCalculationType");
        Assertions.assertEquals(chargeChangedData.get("id"), changes.get("chargeCalculationType"), "Verifying Charge after Modification");

        changes = ChargesHelper.updateCharges(requestSpec, responseSpec, installmentFeeChargeId,
                ChargesHelper.getModifyChargeAsPecentageLoanAmountWithInterestJSON());

        chargeDataAfterChanges = ChargesHelper.getChargeById(requestSpec, responseSpec, installmentFeeChargeId);

        chargeChangedData = (HashMap) chargeDataAfterChanges.get("chargeCalculationType");
        Assertions.assertEquals(chargeChangedData.get("id"), changes.get("chargeCalculationType"), "Verifying Charge after Modification");

        changes = ChargesHelper.updateCharges(requestSpec, responseSpec, installmentFeeChargeId,
                ChargesHelper.getModifyChargeAsPercentageInterestJSON());

        chargeDataAfterChanges = ChargesHelper.getChargeById(requestSpec, responseSpec, installmentFeeChargeId);

        chargeChangedData = (HashMap) chargeDataAfterChanges.get("chargeCalculationType");
        Assertions.assertEquals(chargeChangedData.get("id"), changes.get("chargeCalculationType"), "Verifying Charge after Modification");

        chargeIdAfterDeletion = ChargesHelper.deleteCharge(responseSpec, requestSpec, installmentFeeChargeId);
        Assertions.assertEquals(installmentFeeChargeId, chargeIdAfterDeletion, "Verifying Charge ID after deletion");

        // Testing Creation, Updation and Deletion of Overdue Installment Fee
        // Charge
        final Integer overdueFeeChargeId = ChargesHelper.createCharges(requestSpec, responseSpec, ChargesHelper.getLoanOverdueFeeJSON());
        Assertions.assertNotNull(overdueFeeChargeId);

        // Updating Charge Amount
        changes = ChargesHelper.updateCharges(requestSpec, responseSpec, overdueFeeChargeId, ChargesHelper.getModifyChargeJSON());

        chargeDataAfterChanges = ChargesHelper.getChargeById(requestSpec, responseSpec, overdueFeeChargeId);
        Assertions.assertEquals(chargeDataAfterChanges.get("amount"), changes.get("amount"), "Verifying Charge after Modification");

        changes = ChargesHelper.updateCharges(requestSpec, responseSpec, overdueFeeChargeId,
                ChargesHelper.getModifyChargeAsPecentageAmountJSON());

        chargeDataAfterChanges = ChargesHelper.getChargeById(requestSpec, responseSpec, overdueFeeChargeId);

        chargeChangedData = (HashMap) chargeDataAfterChanges.get("chargePaymentMode");
        Assertions.assertEquals(chargeChangedData.get("id"), changes.get("chargePaymentMode"), "Verifying Charge after Modification");

        chargeChangedData = (HashMap) chargeDataAfterChanges.get("chargeCalculationType");
        Assertions.assertEquals(chargeChangedData.get("id"), changes.get("chargeCalculationType"), "Verifying Charge after Modification");

        changes = ChargesHelper.updateCharges(requestSpec, responseSpec, overdueFeeChargeId,
                ChargesHelper.getModifyChargeAsPecentageLoanAmountWithInterestJSON());

        chargeDataAfterChanges = ChargesHelper.getChargeById(requestSpec, responseSpec, overdueFeeChargeId);

        chargeChangedData = (HashMap) chargeDataAfterChanges.get("chargeCalculationType");
        Assertions.assertEquals(chargeChangedData.get("id"), changes.get("chargeCalculationType"), "Verifying Charge after Modification");

        changes = ChargesHelper.updateCharges(requestSpec, responseSpec, overdueFeeChargeId,
                ChargesHelper.getModifyChargeAsPercentageInterestJSON());

        chargeDataAfterChanges = ChargesHelper.getChargeById(requestSpec, responseSpec, overdueFeeChargeId);

        chargeChangedData = (HashMap) chargeDataAfterChanges.get("chargeCalculationType");
        Assertions.assertEquals(chargeChangedData.get("id"), changes.get("chargeCalculationType"), "Verifying Charge after Modification");

        changes = ChargesHelper.updateCharges(requestSpec, responseSpec, overdueFeeChargeId,
                ChargesHelper.getModifyChargeFeeFrequencyAsYearsJSON());

        chargeDataAfterChanges = ChargesHelper.getChargeById(requestSpec, responseSpec, overdueFeeChargeId);

        chargeChangedData = (HashMap) chargeDataAfterChanges.get("feeFrequency");
        Assertions.assertEquals(chargeChangedData.get("id"), changes.get("feeFrequency"), "Verifying Charge after Modification");

        chargeIdAfterDeletion = ChargesHelper.deleteCharge(responseSpec, requestSpec, overdueFeeChargeId);
        Assertions.assertEquals(overdueFeeChargeId, chargeIdAfterDeletion, "Verifying Charge ID after deletion");
    }

    @Test
    public void testChargesForSavings() {

        // Testing Creation, Updation and Deletion of Specified due date Charge
        final Integer specifiedDueDateChargeId = ChargesHelper.createCharges(requestSpec, responseSpec,
                ChargesHelper.getSavingsSpecifiedDueDateJSON());
        Assertions.assertNotNull(specifiedDueDateChargeId);

        // Updating Charge Amount
        HashMap changes = ChargesHelper.updateCharges(requestSpec, responseSpec, specifiedDueDateChargeId,
                ChargesHelper.getModifyChargeJSON());

        HashMap chargeDataAfterChanges = ChargesHelper.getChargeById(requestSpec, responseSpec, specifiedDueDateChargeId);
        Assertions.assertEquals(chargeDataAfterChanges.get("amount"), changes.get("amount"), "Verifying Charge after Modification");

        Integer chargeIdAfterDeletion = ChargesHelper.deleteCharge(responseSpec, requestSpec, specifiedDueDateChargeId);
        Assertions.assertEquals(specifiedDueDateChargeId, chargeIdAfterDeletion, "Verifying Charge ID after deletion");

        // Testing Creation, Updation and Deletion of Savings Activation Charge
        final Integer savingsActivationChargeId = ChargesHelper.createCharges(requestSpec, responseSpec,
                ChargesHelper.getSavingsActivationFeeJSON());
        Assertions.assertNotNull(savingsActivationChargeId);

        // Updating Charge Amount
        changes = ChargesHelper.updateCharges(requestSpec, responseSpec, savingsActivationChargeId, ChargesHelper.getModifyChargeJSON());

        chargeDataAfterChanges = ChargesHelper.getChargeById(requestSpec, responseSpec, savingsActivationChargeId);
        Assertions.assertEquals(chargeDataAfterChanges.get("amount"), changes.get("amount"), "Verifying Charge after Modification");

        chargeIdAfterDeletion = ChargesHelper.deleteCharge(responseSpec, requestSpec, savingsActivationChargeId);
        Assertions.assertEquals(savingsActivationChargeId, chargeIdAfterDeletion, "Verifying Charge ID after deletion");

        // Testing Creation, Updation and Deletion of Charge for Withdrawal Fee
        final Integer withdrawalFeeChargeId = ChargesHelper.createCharges(requestSpec, responseSpec,
                ChargesHelper.getSavingsWithdrawalFeeJSON());
        Assertions.assertNotNull(withdrawalFeeChargeId);

        // Updating Charge-Calculation-Type to Withdrawal-Fee
        changes = ChargesHelper.updateCharges(requestSpec, responseSpec, withdrawalFeeChargeId,
                ChargesHelper.getModifyWithdrawalFeeSavingsChargeJSON());

        chargeDataAfterChanges = ChargesHelper.getChargeById(requestSpec, responseSpec, withdrawalFeeChargeId);

        HashMap chargeChangedData = (HashMap) chargeDataAfterChanges.get("chargeCalculationType");
        Assertions.assertEquals(chargeChangedData.get("id"), changes.get("chargeCalculationType"), "Verifying Charge after Modification");

        chargeIdAfterDeletion = ChargesHelper.deleteCharge(responseSpec, requestSpec, withdrawalFeeChargeId);
        Assertions.assertEquals(withdrawalFeeChargeId, chargeIdAfterDeletion, "Verifying Charge ID after deletion");

        // Testing Creation, Updation and Deletion of Charge for Annual Fee
        final Integer annualFeeChargeId = ChargesHelper.createCharges(requestSpec, responseSpec, ChargesHelper.getSavingsAnnualFeeJSON());
        Assertions.assertNotNull(annualFeeChargeId);

        // Updating Charge Amount
        changes = ChargesHelper.updateCharges(requestSpec, responseSpec, annualFeeChargeId, ChargesHelper.getModifyChargeJSON());

        chargeDataAfterChanges = ChargesHelper.getChargeById(requestSpec, responseSpec, annualFeeChargeId);
        Assertions.assertEquals(chargeDataAfterChanges.get("amount"), changes.get("amount"), "Verifying Charge after Modification");

        chargeIdAfterDeletion = ChargesHelper.deleteCharge(responseSpec, requestSpec, annualFeeChargeId);
        Assertions.assertEquals(annualFeeChargeId, chargeIdAfterDeletion, "Verifying Charge ID after deletion");

        // Testing Creation, Updation and Deletion of Charge for Monthly Fee
        final Integer monthlyFeeChargeId = ChargesHelper.createCharges(requestSpec, responseSpec, ChargesHelper.getSavingsMonthlyFeeJSON());
        Assertions.assertNotNull(monthlyFeeChargeId);

        // Updating Charge Amount
        changes = ChargesHelper.updateCharges(requestSpec, responseSpec, monthlyFeeChargeId, ChargesHelper.getModifyChargeJSON());

        chargeDataAfterChanges = ChargesHelper.getChargeById(requestSpec, responseSpec, monthlyFeeChargeId);
        Assertions.assertEquals(chargeDataAfterChanges.get("amount"), changes.get("amount"), "Verifying Charge after Modification");

        chargeIdAfterDeletion = ChargesHelper.deleteCharge(responseSpec, requestSpec, monthlyFeeChargeId);
        Assertions.assertEquals(monthlyFeeChargeId, chargeIdAfterDeletion, "Verifying Charge ID after deletion");

        // Testing Creation, Updation and Deletion of Charge for Overdraft Fee
        final Integer overdraftFeeChargeId = ChargesHelper.createCharges(requestSpec, responseSpec,
                ChargesHelper.getSavingsOverdraftFeeJSON());
        Assertions.assertNotNull(overdraftFeeChargeId);

        // Updating Charge Amount
        changes = ChargesHelper.updateCharges(requestSpec, responseSpec, overdraftFeeChargeId, ChargesHelper.getModifyChargeJSON());

        chargeDataAfterChanges = ChargesHelper.getChargeById(requestSpec, responseSpec, overdraftFeeChargeId);
        Assertions.assertEquals(chargeDataAfterChanges.get("amount"), changes.get("amount"), "Verifying Charge after Modification");

        chargeIdAfterDeletion = ChargesHelper.deleteCharge(responseSpec, requestSpec, overdraftFeeChargeId);
        Assertions.assertEquals(overdraftFeeChargeId, chargeIdAfterDeletion, "Verifying Charge ID after deletion");
    }
}
