// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>

namespace search { class IDestructorCallback; }

namespace proton {

/**
 * Interface used to limit the number of outstanding move operations a blockable maintenance job can have.
 */
struct IMoveOperationLimiter {
    virtual ~IMoveOperationLimiter() {}
    virtual std::shared_ptr<search::IDestructorCallback> beginOperation() = 0;
};

}
