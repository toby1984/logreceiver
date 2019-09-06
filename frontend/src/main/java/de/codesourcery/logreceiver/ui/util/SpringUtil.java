package de.codesourcery.logreceiver.ui.util;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class SpringUtil
{
    public static void onSuccessfulCommit(Runnable r) {

        TransactionSynchronizationManager.registerSynchronization( new TransactionSynchronization()
        {
            @Override
            public void afterCompletion(int status)
            {
                if ( status == STATUS_COMMITTED ) {
                   r.run();
                }
            }
        });
    }
}
