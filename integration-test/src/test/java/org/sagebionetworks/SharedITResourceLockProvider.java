package org.sagebionetworks;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLocksProvider;

public class SharedITResourceLockProvider implements ResourceLocksProvider {

    @Override
    public Set<Lock> provideForClass(Class<?> testClass) {
        Set<Lock> locks = new HashSet<>();
        locks.add(getDownStatusLock(testClass));
        locks.add(getTwoFactorAuthLock(testClass));
        return locks;
    }

    private Lock getDownStatusLock(Class<?> testClass) {
        if (testClass.equals(IT101Administration.class)) {
            return new Lock("DOWN_STATUS", ResourceAccessMode.READ_WRITE);
        }
        return new Lock("DOWN_STATUS", ResourceAccessMode.READ);
    }

    private Lock getTwoFactorAuthLock(Class<?> testClass) {
        if (testClass.equals(ITTwoFactorAuthTest.class)) {
            return new Lock("TWO_FACTOR_AUTH", ResourceAccessMode.READ_WRITE);
        }
        return new Lock("TWO_FACTOR_AUTH", ResourceAccessMode.READ);
    }
}