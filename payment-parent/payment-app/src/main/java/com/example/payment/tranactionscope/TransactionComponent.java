/* Licensed under Apache-2.0 2023. */
package com.example.payment.tranactionscope;

import com.example.payment.tranactionscope.repo.Repo;
import com.example.payment.tranactionscope.repo.RepoModule;
import dagger.Subcomponent;

@TransactionScope
@Subcomponent(modules = {RepoModule.class})
public interface TransactionComponent {

  Repo repo();

  @Subcomponent.Builder
  interface Builder {

    TransactionComponent build();
  }
}
