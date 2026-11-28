package org.crux.ui.presenter

import androidx.compose.runtime.Composable
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import kotlinx.coroutines.flow.Flow

public abstract class PresenterBase<Model : Any> {
    public val moleculeFlow: Flow<Model> by lazy {
        moleculeFlow(RecompositionMode.Immediate) {
            body()
        }
    }

    @Composable
    public abstract fun body(): Model
}

@Composable
public operator fun <Model : Any> PresenterBase<Model>.invoke(): Model = body()
