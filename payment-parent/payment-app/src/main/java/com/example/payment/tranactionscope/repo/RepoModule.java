/* Licensed under Apache-2.0 2023. */
package com.example.payment.tranactionscope.repo;

import dagger.Binds;
import dagger.Module;

@Module
public interface RepoModule {

  @Binds
  Repo repo(RepoImpl repoImpl);
}
