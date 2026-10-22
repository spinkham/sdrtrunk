/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 * ****************************************************************************
 */

package io.github.dsheirer.module.decode.p25.phase2.message.isch;

import io.github.dsheirer.bits.BinaryMessage;
import io.github.dsheirer.bits.CorrectedBinaryMessage;
import io.github.dsheirer.bits.IntField;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * I-ISCH nearest-codeword decoding.  The codewords are 40 bits with a minimum distance of 16, so every pattern of up
 * to 7 bit errors must decode back to the transmitted message.
 */
public class ISCHDecoderTest
{
    private static final long CODE_WORD_OFFSET = 0x184229d461L; //ISCHDecoder's, applied to every codeword
    private static final IntField CHANNEL_NUMBER = IntField.range(2, 3);
    private static final IntField ISCH_LOCATION = IntField.range(4, 5);

    private record Codeword(int value, long codeword, int timeslot) {}

    /**
     * Every valid codeword for both timeslots, built the same way the decoder builds its lookup maps.
     */
    private static List<Codeword> codewords()
    {
        List<Codeword> codewords = new ArrayList<>();

        for(int x = 0; x < 512; x++)
        {
            BinaryMessage message = new BinaryMessage(9);
            message.load(0, 9, x);

            int timeslot = message.getInt(CHANNEL_NUMBER) + 1;

            if(message.getInt(ISCH_LOCATION) != 3 && (timeslot == 1 || timeslot == 2))
            {
                long codeword = ISCHDecoder.getCodeWord(message) ^ CODE_WORD_OFFSET;
                codewords.add(new Codeword(x, codeword, timeslot));
            }
        }

        return codewords;
    }

    private static CorrectedBinaryMessage decode(ISCHDecoder decoder, long codeword, int timeslot)
    {
        BinaryMessage message = new BinaryMessage(40);
        message.load(0, 40, codeword);
        return decoder.decode(message, timeslot);
    }

    private static void assertDecodes(ISCHDecoder decoder, Codeword expected, long received, int errors)
    {
        CorrectedBinaryMessage decoded = decode(decoder, received, expected.timeslot());
        assertEquals(expected.value(), decoded.getInt(0, 8), "TS" + expected.timeslot() + " message " +
            expected.value() + " with " + errors + " bit errors");
        assertEquals(errors, decoded.getCorrectedBitCount());
    }

    @Test
    public void errorFreeCodewordsDecode()
    {
        ISCHDecoder decoder = new ISCHDecoder();

        for(Codeword codeword: codewords())
        {
            assertDecodes(decoder, codeword, codeword.codeword(), 0);
        }
    }

    @Test
    public void singleBitErrorsAreCorrected()
    {
        ISCHDecoder decoder = new ISCHDecoder();

        for(Codeword codeword: codewords())
        {
            for(int a = 0; a < 40; a++)
            {
                assertDecodes(decoder, codeword, codeword.codeword() ^ (1L << a), 1);
            }
        }
    }

    @Test
    public void doubleBitErrorsAreCorrected()
    {
        ISCHDecoder decoder = new ISCHDecoder();

        for(Codeword codeword: codewords())
        {
            for(int a = 0; a < 40; a++)
            {
                for(int b = a + 1; b < 40; b++)
                {
                    assertDecodes(decoder, codeword, codeword.codeword() ^ (1L << a) ^ (1L << b), 2);
                }
            }
        }
    }

    @Test
    public void sevenBitErrorsAreCorrected()
    {
        ISCHDecoder decoder = new ISCHDecoder();

        for(Codeword codeword: codewords())
        {
            long errors = 0;

            for(int bit = 0; bit < 7; bit++)
            {
                errors |= 1L << ((codeword.value() + bit * 5) % 40);
            }

            assertDecodes(decoder, codeword, codeword.codeword() ^ errors, 7);
        }
    }

    /**
     * A zeroed I-ISCH is not a codeword and must not pass the caller's validity gate (fewer than 3 corrected bits).
     */
    @Test
    public void zeroedFieldIsNotValid()
    {
        ISCHDecoder decoder = new ISCHDecoder();

        for(int timeslot = 1; timeslot <= 2; timeslot++)
        {
            assertFalse(decode(decoder, 0L, timeslot).getCorrectedBitCount() < 3, "TS" + timeslot);
        }
    }

    @Test
    public void bothTimeslotsAreCovered()
    {
        List<Codeword> codewords = codewords();
        assertTrue(codewords.stream().anyMatch(codeword -> codeword.timeslot() == 1));
        assertTrue(codewords.stream().anyMatch(codeword -> codeword.timeslot() == 2));
    }
}
