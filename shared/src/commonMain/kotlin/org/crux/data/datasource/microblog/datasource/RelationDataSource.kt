package org.crux.data.datasource.microblog.datasource

import org.crux.data.datasource.microblog.handler.RelationHandler
import org.crux.data.datasource.microblog.loader.RelationActionType
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public interface RelationDataSource {
    public val relationHandler: RelationHandler
    public val supportedRelationTypes: Set<RelationActionType>
}
