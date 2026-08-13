package com.achievementfactory.ui.celebration

/**
 * The four stages of the single-item completion sequence (D-21), in the order they always
 * resolve — sequenced, never simultaneous, and never skipped even when reduced motion collapses
 * every duration to near-instant (D-25). [SETTLED] is what a caller gates the Done-section sink
 * on (D-06) — never a fixed delay.
 */
enum class CelebrationStage {
    RING_CLOSED,
    CHECK_MORPHED,
    FLAME_TICKED,
    SETTLED,
}
