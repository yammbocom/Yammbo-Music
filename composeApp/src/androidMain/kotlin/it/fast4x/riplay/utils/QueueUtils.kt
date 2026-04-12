package it.fast4x.riplay.utils

import com.yambo.music.R
import it.fast4x.riplay.enums.QueueLoopType

fun setQueueLoopState(currentState: QueueLoopType): QueueLoopType {
    val newState =
        when (currentState) {
            QueueLoopType.RepeatAll -> QueueLoopType.RepeatOne
            QueueLoopType.RepeatOne -> QueueLoopType.RepeatAll
            else -> QueueLoopType.RepeatAll
        }

    return newState
}

fun getIconQueueLoopState(currentState: QueueLoopType): Int {
    val current =
        when (currentState) {
            QueueLoopType.RepeatOne -> R.drawable.repeatone
            else -> R.drawable.infinite
        }

    return current
}

