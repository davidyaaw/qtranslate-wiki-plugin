// SPDX-License-Identifier: MPL-2.0
// Derived from QTranslate (https://github.com/ahatem/QTranslate), MPL-2.0.
//
// Forward-compatible copy of an optional host interface.
//
// QTranslate's plugin classloader is parent-first. On a host that already provides
// LanguageSwitchDictionary (the patched host), that copy wins and the plugin's service is
// castable to it. On an older host that does not, this copy lets the plugin load and keeps
// the Wikipedia lookup working; the older host simply never asks for a language switch.
package com.github.ahatem.qtranslate.api.dictionary

import com.github.ahatem.qtranslate.api.language.LanguageCode

/**
 * Optional behaviour for dictionaries that can present the same headword in more than one
 * language and let the reader choose which edition to read.
 *
 * The host shows one control per language in [switchLanguages] and asks for a lookup with
 * [DictionaryRequest.language] set to the chosen language.
 */
public interface LanguageSwitchDictionary : Dictionary {

    /**
     * Languages offered by the switch, in display order.
     *
     * Must not be empty and must not contain [LanguageCode.AUTO].
     */
    public val switchLanguages: List<LanguageCode>
}
