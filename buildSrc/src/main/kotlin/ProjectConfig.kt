/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

const val NEWPIPE_VERSION_SDK_COMPILE_MAJOR = 36
const val NEWPIPE_VERSION_SDK_COMPILE_MINOR = 1
const val NEWPIPE_VERSION_SDK_MIN = 23
const val NEWPIPE_VERSION_SDK_TARGET = 35

const val NEWPIPE_VERSION_CODE = 1100
const val NEWPIPE_VERSION_NAME = "1.0.0"

// The namespace determines the package of generated R/BuildConfig classes.
// The whole app codebase still references org.schabi.newpipe.R and
// org.schabi.newpipe.BuildConfig, so the namespace must stay unchanged.
const val NEWPIPE_NAMESPACE = "org.schabi.newpipe"
// The application ID is the unique identifier of the installed app and can
// safely be customized per fork.
const val NEWPIPE_APPLICATION_ID_OLD = "fr.arthonetwork.onewpipe"
const val NEWPIPE_APPLICATION_ID_NEW = "fr.arthonetwork.onewpipe"
