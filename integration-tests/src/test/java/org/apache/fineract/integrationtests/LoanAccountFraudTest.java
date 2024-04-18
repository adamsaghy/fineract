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
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.client.models.GetLoansLoanIdResponse;
import org.apache.fineract.client.models.PostLoanProductsRequest;
import org.apache.fineract.client.models.PostLoanProductsResponse;
import org.apache.fineract.client.models.PostLoansLoanIdResponse;
import org.apache.fineract.client.models.PostLoansResponse;
import org.apache.fineract.client.models.PutLoansLoanIdResponse;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.loans.LoanTestLifecycleExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@Slf4j
@ExtendWith(LoanTestLifecycleExtension.class)
public class LoanAccountFraudTest extends BaseLoanIntegrationTest {

    private static final double AMOUNT = 100.0;
    private static final String COMMAND = "markAsFraud";
    private LocalDate todaysDate;
    private String operationDate;

    @BeforeEach
    public void setup() {
        Utils.initializeRESTAssured();
        this.todaysDate = Utils.getLocalDateOfTenant();
        this.operationDate = Utils.dateFormatter.format(this.todaysDate);
    }

    @Test
    public void testMarkLoanAsFraud() {
        runAt(operationDate, () -> {

            final Long clientId = CLIENT_HELPER.createClient(ClientHelper.defaultClientCreationRequest()).getClientId();

            PostLoanProductsRequest loanProductsRequest = createOnePeriod30DaysLongNoInterestPeriodicAccrualProduct();
            PostLoanProductsResponse loanProductResponse = LOAN_PRODUCT_HELPER.createLoanProduct(loanProductsRequest);

            PostLoansResponse postLoansResponse = LOAN_TRANSACTION_HELPER
                    .applyLoan(applyLoanRequest(clientId, loanProductResponse.getResourceId(), operationDate, AMOUNT, 1));
            Integer loanId = postLoansResponse.getLoanId().intValue();

            GetLoansLoanIdResponse getLoansLoanIdResponse = LOAN_TRANSACTION_HELPER.getLoan(REQUEST_SPEC, RESPONSE_SPEC, loanId);
            assertNotNull(getLoansLoanIdResponse);

            // Default values Not Null and False
            assertNotNull(getLoansLoanIdResponse.getFraud());
            assertEquals(Boolean.FALSE, getLoansLoanIdResponse.getFraud());

            String payload = LOAN_TRANSACTION_HELPER.getLoanFraudPayloadAsJSON("fraud", "true");
            PutLoansLoanIdResponse putLoansLoanIdResponse = LOAN_TRANSACTION_HELPER.modifyLoanCommand(loanId, COMMAND, payload,
                    RESPONSE_SPEC);
            assertNotNull(putLoansLoanIdResponse);

            getLoansLoanIdResponse = LOAN_TRANSACTION_HELPER.getLoan(REQUEST_SPEC, RESPONSE_SPEC, loanId);
            assertNotNull(getLoansLoanIdResponse);
            assertNotNull(getLoansLoanIdResponse.getFraud());
            assertEquals(Boolean.TRUE, getLoansLoanIdResponse.getFraud());
            String statusCode = getLoansLoanIdResponse.getStatus().getCode();
            log.info("Loan with Id {} is with Status {}", getLoansLoanIdResponse.getId(), statusCode);

            payload = LOAN_TRANSACTION_HELPER.getLoanFraudPayloadAsJSON("fraud", "false");
            putLoansLoanIdResponse = LOAN_TRANSACTION_HELPER.modifyLoanCommand(loanId, COMMAND, payload, RESPONSE_SPEC);
            assertNotNull(putLoansLoanIdResponse);

            getLoansLoanIdResponse = LOAN_TRANSACTION_HELPER.getLoan(REQUEST_SPEC, RESPONSE_SPEC, loanId);
            assertNotNull(getLoansLoanIdResponse);
            assertNotNull(getLoansLoanIdResponse.getFraud());
            assertEquals(Boolean.FALSE, getLoansLoanIdResponse.getFraud());
            statusCode = getLoansLoanIdResponse.getStatus().getCode();
            log.info("Loan with Id {} is with Status {}", getLoansLoanIdResponse.getId(), statusCode);

            // Approve the Loan active
            PostLoansLoanIdResponse approvedLoanResult = LOAN_TRANSACTION_HELPER.approveLoan(postLoansResponse.getResourceId(),
                    approveLoanRequest(AMOUNT, operationDate));
            assertNotNull(approvedLoanResult);

            payload = LOAN_TRANSACTION_HELPER.getLoanFraudPayloadAsJSON("fraud", "true");
            putLoansLoanIdResponse = LOAN_TRANSACTION_HELPER.modifyLoanCommand(loanId, COMMAND, payload, RESPONSE_SPEC);
            assertNotNull(putLoansLoanIdResponse);

            getLoansLoanIdResponse = LOAN_TRANSACTION_HELPER.getLoan(REQUEST_SPEC, RESPONSE_SPEC, loanId);
            assertNotNull(getLoansLoanIdResponse);
            assertNotNull(getLoansLoanIdResponse.getFraud());
            assertEquals(Boolean.TRUE, getLoansLoanIdResponse.getFraud());
            statusCode = getLoansLoanIdResponse.getStatus().getCode();
            log.info("Loan with Id {} is with Status {}", getLoansLoanIdResponse.getId(), statusCode);

            payload = LOAN_TRANSACTION_HELPER.getLoanFraudPayloadAsJSON("fraud", "false");
            putLoansLoanIdResponse = LOAN_TRANSACTION_HELPER.modifyLoanCommand(loanId, COMMAND, payload, RESPONSE_SPEC);
            assertNotNull(putLoansLoanIdResponse);

            // Default values Not Null and False
            getLoansLoanIdResponse = LOAN_TRANSACTION_HELPER.getLoan(REQUEST_SPEC, RESPONSE_SPEC, loanId);
            assertNotNull(getLoansLoanIdResponse);
            assertNotNull(getLoansLoanIdResponse.getFraud());
            assertEquals(Boolean.FALSE, getLoansLoanIdResponse.getFraud());
            statusCode = getLoansLoanIdResponse.getStatus().getCode();
            log.info("Loan with Id {} is with Status {}", getLoansLoanIdResponse.getId(), statusCode);

            disburseLoan(loanId.longValue(), BigDecimal.valueOf(AMOUNT), operationDate);

            // Mark On the Fraud
            payload = LOAN_TRANSACTION_HELPER.getLoanFraudPayloadAsJSON("fraud", "true");
            putLoansLoanIdResponse = LOAN_TRANSACTION_HELPER.modifyLoanCommand(loanId, COMMAND, payload, RESPONSE_SPEC);
            assertNotNull(putLoansLoanIdResponse);

            getLoansLoanIdResponse = LOAN_TRANSACTION_HELPER.getLoan(REQUEST_SPEC, RESPONSE_SPEC, loanId);
            assertNotNull(getLoansLoanIdResponse);
            assertNotNull(getLoansLoanIdResponse.getFraud());
            assertEquals(Boolean.TRUE, getLoansLoanIdResponse.getFraud());

            // Mark Off the Fraud
            payload = LOAN_TRANSACTION_HELPER.getLoanFraudPayloadAsJSON("fraud", "false");
            putLoansLoanIdResponse = LOAN_TRANSACTION_HELPER.modifyLoanCommand(loanId, COMMAND, payload, this.RESPONSE_SPEC);
            assertNotNull(putLoansLoanIdResponse);

            getLoansLoanIdResponse = LOAN_TRANSACTION_HELPER.getLoan(REQUEST_SPEC, RESPONSE_SPEC, loanId);
            assertNotNull(getLoansLoanIdResponse);
            assertNotNull(getLoansLoanIdResponse.getFraud());
            assertEquals(Boolean.FALSE, getLoansLoanIdResponse.getFraud());

            payload = LOAN_TRANSACTION_HELPER.getLoanFraudPayloadAsJSON("fraud", "true");
            putLoansLoanIdResponse = LOAN_TRANSACTION_HELPER.modifyLoanCommand(loanId, COMMAND, payload, RESPONSE_SPEC);
            assertNotNull(putLoansLoanIdResponse);

            getLoansLoanIdResponse = LOAN_TRANSACTION_HELPER.getLoan(REQUEST_SPEC, RESPONSE_SPEC, loanId);
            assertNotNull(getLoansLoanIdResponse);
            assertNotNull(getLoansLoanIdResponse.getFraud());
            assertEquals(Boolean.TRUE, getLoansLoanIdResponse.getFraud());
            statusCode = getLoansLoanIdResponse.getStatus().getCode();
            log.info("Loan with Id {} is with Status {}", getLoansLoanIdResponse.getId(), statusCode);

            undoDisbursement(loanId);

            getLoansLoanIdResponse = LOAN_TRANSACTION_HELPER.getLoan(REQUEST_SPEC, RESPONSE_SPEC, loanId);
            assertNotNull(getLoansLoanIdResponse);
            assertNotNull(getLoansLoanIdResponse.getFraud());
            assertEquals(Boolean.TRUE, getLoansLoanIdResponse.getFraud());
            statusCode = getLoansLoanIdResponse.getStatus().getCode();
            log.info("Loan with Id {} is with Status {}", getLoansLoanIdResponse.getId(), statusCode);
        });
    }
}
