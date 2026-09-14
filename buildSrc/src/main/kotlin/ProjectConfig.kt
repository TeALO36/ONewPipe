/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

const val NEWPIPE_VERSION_SDK_COMPILE_MAJOR = 37
const val NEWPIPE_VERSION_SDK_COMPILE_MINOR = 0
const val NEWPIPE_VERSION_SDK_MIN = 23
const val NEWPIPE_VERSION_SDK_TARGET = 35

// ONewPipe versioning is independent of upstream NewPipe. The upstream base
// currently merged into this fork is NewPipe 0.29.1 (dev).
const val NEWPIPE_VERSION_CODE = 1300
const val NEWPIPE_VERSION_NAME = "1.3.0"
const val UPSTREAM_NEWPIPE_VERSION_NAME = "0.29.1"

// The namespace determines the package of generated R/BuildConfig classes.
// The whole app codebase still references org.schabi.newpipe.R and
// org.schabi.newpipe.BuildConfig, so the namespace must stay unchanged.
const val NEWPIPE_NAMESPACE = "org.schabi.newpipe"
// The application ID is the unique identifier of the installed app and can
// safely be customized per fork.
const val NEWPIPE_APPLICATION_ID_OLD = "fr.arthonetwork.onewpipe"
const val NEWPIPE_APPLICATION_ID_NEW = "fr.arthonetwork.onewpipe"
