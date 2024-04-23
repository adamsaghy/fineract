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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.apache.fineract.client.models.PostLoanProductsRequest;
import org.apache.fineract.client.models.PostLoanProductsResponse;
import org.apache.fineract.client.models.PostLoansLoanIdTransactionsRequest;
import org.apache.fineract.client.models.PostLoansLoanIdTransactionsResponse;
import org.apache.fineract.client.models.PostLoansLoanIdTransactionsTransactionIdRequest;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.CommonConstants;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.accounting.Account;
import org.apache.fineract.integrationtests.common.loans.LoanTestLifecycleExtension;
import org.apache.fineract.integrationtests.common.system.CodeHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(LoanTestLifecycleExtension.class)
public class LoanTransactionReverseReplayChargeOffTest extends BaseLoanIntegrationTest {

    @Test
    public void loanTransactionReverseReplayWithChargeOff() {
        runAt("4 October 2022", () -> {
            final Account assetAccount = ACCOUNT_HELPER.createAssetAccount();
            final Account chargeOffFraudExpenseAccount = ACCOUNT_HELPER.createExpenseAccount();
            final Account chargeOffExpenseAccount = ACCOUNT_HELPER.createExpenseAccount();

            // Loan ExternalId
            String loanExternalIdStr = UUID.randomUUID().toString();

            // Client and Loan account creation

            final Long clientId = CLIENT_HELPER.createClient(ClientHelper.defaultClientCreationRequest()).getClientId();

            PostLoanProductsRequest loanProductsRequest = createOnePeriod30DaysLongNoInterestPeriodicAccrualProduct()
                    .chargeOffExpenseAccountId(chargeOffExpenseAccount.getAccountID().longValue())
                    .chargeOffFraudExpenseAccountId(chargeOffFraudExpenseAccount.getAccountID().longValue())
                    .loanPortfolioAccountId(assetAccount.getAccountID().longValue());
            PostLoanProductsResponse loanProductResponse = LOAN_PRODUCT_HELPER.createLoanProduct(loanProductsRequest);

            Long loanId = applyAndApproveLoan(clientId, loanProductResponse.getResourceId(), "2 September 2022", 1000.0, 1,
                    postLoansRequest -> {
                        postLoansRequest.externalId(loanExternalIdStr);
                    });

            disburseLoan(loanId, BigDecimal.valueOf(1000.00), "2 September 2022");

            // make repayment
            String loanTransactionExternalIdStr = UUID.randomUUID().toString();
            PostLoansLoanIdTransactionsResponse repaymentTransaction = LOAN_TRANSACTION_HELPER.makeLoanRepayment(loanExternalIdStr,
                    new PostLoansLoanIdTransactionsRequest().dateFormat(CommonConstants.DATE_FORMAT).transactionDate("03 October 2022")
                            .locale("en").transactionAmount(10.0).externalId(loanTransactionExternalIdStr));

            // mark loan as fraud
            final String command = "markAsFraud";
            String payload = LOAN_TRANSACTION_HELPER.getLoanFraudPayloadAsJSON("fraud", "true");
            LOAN_TRANSACTION_HELPER.modifyLoanCommand(loanId.intValue(), command, payload, RESPONSE_SPEC);

            // charge-off loan
            String randomText = Utils.randomStringGenerator("en", 5) + Utils.randomNumberGenerator(6)
                    + Utils.randomStringGenerator("is", 5);
            Integer chargeOffReasonId = CodeHelper.createChargeOffCodeValue(REQUEST_SPEC, RESPONSE_SPEC, randomText, 1);
            String transactionExternalId = UUID.randomUUID().toString();
            PostLoansLoanIdTransactionsResponse chargeOffTransaction = LOAN_TRANSACTION_HELPER.chargeOffLoan((long) loanId,
                    new PostLoansLoanIdTransactionsRequest().transactionDate("4 October 2022").locale("en")
                            .dateFormat(CommonConstants.DATE_FORMAT).externalId(transactionExternalId)
                            .chargeOffReasonId((long) chargeOffReasonId));

            updateBusinessDate("6 October 2022");

            LOAN_TRANSACTION_HELPER.reverseLoanTransaction(loanExternalIdStr, repaymentTransaction.getResourceId(),
                    new PostLoansLoanIdTransactionsTransactionIdRequest().transactionDate("6 October 2022").locale("en")
                            .dateFormat(CommonConstants.DATE_FORMAT).transactionAmount(0.0));

            ArrayList<HashMap> journalEntriesForChargeOffTransaction = JOURNAL_ENTRY_HELPER
                    .getJournalEntriesByTransactionId("L" + chargeOffTransaction.getResourceId());
            assertNotNull(journalEntriesForChargeOffTransaction);

            List<HashMap> assetAccountJournalEntries = journalEntriesForChargeOffTransaction.stream() //
                    .filter(journalEntry -> assetAccount.getAccountID().equals(journalEntry.get("glAccountId"))) //
                    .toList();

            List<HashMap> expenseAccountJournalEntries = journalEntriesForChargeOffTransaction.stream() //
                    .filter(journalEntry -> chargeOffFraudExpenseAccount.getAccountID().equals(journalEntry.get("glAccountId"))) //
                    .toList();

            assertEquals(2, assetAccountJournalEntries.size());
            assertEquals(2, expenseAccountJournalEntries.size());
        });
    }
}
