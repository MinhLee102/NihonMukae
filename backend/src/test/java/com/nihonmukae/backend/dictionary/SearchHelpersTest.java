package com.nihonmukae.backend.dictionary;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SearchHelpersTest {

    @Test
    void romajiBasicWords() {
        assertEquals("にほん", Romaji.toHiragana("nihon"));
        assertEquals("たべる", Romaji.toHiragana("taberu"));
        assertEquals("すし", Romaji.toHiragana("sushi"));
        assertEquals("とうきょう", Romaji.toHiragana("toukyou"));
        assertEquals("とうきょう", Romaji.toHiragana("tōkyō"));
        assertEquals("がっこう", Romaji.toHiragana("gakkou"));
        assertEquals("まっちゃ", Romaji.toHiragana("matcha"));
        assertEquals("こんにちは", Romaji.toHiragana("konnichiha"));
        assertEquals("しんぶん", Romaji.toHiragana("shinbun"));
        assertEquals("きょう", Romaji.toHiragana("kyou"));
        assertEquals("あん", Romaji.toHiragana("an"));
        assertEquals("らーめん", Romaji.toHiragana("ra-men"));
    }

    @Test
    void romajiRejectsNonRomaji() {
        assertNull(Romaji.toHiragana("to eat"));
        assertNull(Romaji.toHiragana("eat"));
        assertNull(Romaji.toHiragana("nhật bản"));
        assertNull(Romaji.toHiragana("日本"));
        assertNull(Romaji.toHiragana("a"));
        assertNull(Romaji.toHiragana("xyz"));
    }

    @Test
    void stripsVietnameseAccents() {
        assertEquals("nhat ban", Romaji.stripAccents("nhật bản"));
        assertEquals("dia vuc", Romaji.stripAccents("địa vực"));
        assertEquals("to eat", Romaji.stripAccents("to eat"));
    }

    @Test
    void deinflectsConjugatedWords() {
        Deinflector d = new Deinflector();
        assertEquals(List.of("食べる"), d.baseForms("食べました"));
        assertEquals(List.of("高い"), d.baseForms("高かった"));
        assertEquals(List.of("行く"), d.baseForms("行っている"));
        assertEquals(List.of("飲む"), d.baseForms("飲まない"));
    }

    @Test
    void doesNotDeinflectOtherInput() {
        Deinflector d = new Deinflector();
        assertEquals(List.of(), d.baseForms("食べる"));
        assertEquals(List.of(), d.baseForms("食べ物"));
        assertEquals(List.of(), d.baseForms("暗号化"));
        assertEquals(List.of(), d.baseForms("日本語を勉強する"));
        assertEquals(List.of(), d.baseForms("nihon"));
        assertEquals(List.of(), d.baseForms(""));
    }
}
