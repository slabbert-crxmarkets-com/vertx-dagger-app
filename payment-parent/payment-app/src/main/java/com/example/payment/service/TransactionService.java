/* Licensed under Apache-2.0 2023. */
package com.example.payment.service;

import com.example.payment.tranactionscope.TransactionComponent;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
public class TransactionService {

  private final Provider<TransactionComponent.Builder> transactionProvider;

  @Inject
  TransactionService(Provider<TransactionComponent.Builder> transactionProvider) {
    this.transactionProvider = transactionProvider;
  }

  public void exec() {
    // here i want to start a transaction scoped only to this provider
    TransactionComponent transactionComponent = transactionProvider.get().build();
    // then all the repositories inside will use the same transaction
    // and i don't have to pass around the transaction object

    transactionComponent.repo().save();
  }
}
