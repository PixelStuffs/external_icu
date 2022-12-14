/* GENERATED SOURCE. DO NOT MODIFY. */
// © 2018 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
package android.icu.number;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.AttributedCharacterIterator;
import java.util.Arrays;

import android.icu.impl.FormattedStringBuilder;
import android.icu.impl.FormattedValueStringBuilderImpl;
import android.icu.impl.number.DecimalQuantity;
import android.icu.number.NumberRangeFormatter.RangeIdentityResult;
import android.icu.text.ConstrainedFieldPosition;
import android.icu.text.FormattedValue;
import android.icu.text.PluralRules.IFixedDecimal;
import android.icu.util.ICUUncheckedIOException;

/**
 * The result of a number range formatting operation. This class allows the result to be exported in several data types,
 * including a String, an AttributedCharacterIterator, and a BigDecimal.
 *
 * Instances of this class are immutable and thread-safe.
 *
 * @author sffc
 * @see NumberRangeFormatter
 */
public class FormattedNumberRange implements FormattedValue {
    final FormattedStringBuilder string;
    final DecimalQuantity quantity1;
    final DecimalQuantity quantity2;
    final RangeIdentityResult identityResult;

    FormattedNumberRange(FormattedStringBuilder string, DecimalQuantity quantity1, DecimalQuantity quantity2,
            RangeIdentityResult identityResult) {
        this.string = string;
        this.quantity1 = quantity1;
        this.quantity2 = quantity2;
        this.identityResult = identityResult;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return string.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <A extends Appendable> A appendTo(A appendable) {
        try {
            appendable.append(string);
        } catch (IOException e) {
            // Throw as an unchecked exception to avoid users needing try/catch
            throw new ICUUncheckedIOException(e);
        }
        return appendable;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public char charAt(int index) {
        return string.charAt(index);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int length() {
        return string.length();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CharSequence subSequence(int start, int end) {
        return string.subString(start, end);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean nextPosition(ConstrainedFieldPosition cfpos) {
        return FormattedValueStringBuilderImpl.nextPosition(string, cfpos, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AttributedCharacterIterator toCharacterIterator() {
        return FormattedValueStringBuilderImpl.toCharacterIterator(string, null);
    }

    /**
     * Export the first formatted number as a BigDecimal. This endpoint is useful for obtaining the exact number being
     * printed after scaling and rounding have been applied by the number range formatting pipeline.
     *
     * @return A BigDecimal representation of the first formatted number.
     * @see NumberRangeFormatter
     * @see #getSecondBigDecimal
     */
    public BigDecimal getFirstBigDecimal() {
        return quantity1.toBigDecimal();
    }

    /**
     * Export the second formatted number as a BigDecimal. This endpoint is useful for obtaining the exact number being
     * printed after scaling and rounding have been applied by the number range formatting pipeline.
     *
     * @return A BigDecimal representation of the second formatted number.
     * @see NumberRangeFormatter
     * @see #getFirstBigDecimal
     */
    public BigDecimal getSecondBigDecimal() {
        return quantity2.toBigDecimal();
    }

    /**
     * Returns whether the pair of numbers was successfully formatted as a range or whether an identity fallback was
     * used. For example, if the first and second number were the same either before or after rounding occurred, an
     * identity fallback was used.
     *
     * @return A RangeIdentityType indicating the resulting identity situation in the formatted number range.
     * @see NumberRangeFormatter
     * @see NumberRangeFormatter.RangeIdentityFallback
     */
    public RangeIdentityResult getIdentityResult() {
        return identityResult;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        // FormattedStringBuilder and BigDecimal are mutable, so we can't call
        // #equals() or #hashCode() on them directly.
        return Arrays.hashCode(string.toCharArray()) ^ Arrays.hashCode(string.toFieldArray())
                ^ quantity1.toBigDecimal().hashCode() ^ quantity2.toBigDecimal().hashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object other) {
        if (this == other)
            return true;
        if (other == null)
            return false;
        if (!(other instanceof FormattedNumberRange))
            return false;
        // FormattedStringBuilder and BigDecimal are mutable, so we can't call
        // #equals() or #hashCode() on them directly.
        FormattedNumberRange _other = (FormattedNumberRange) other;
        return string.contentEquals(_other.string)
                && quantity1.toBigDecimal().equals(_other.quantity1.toBigDecimal())
                && quantity2.toBigDecimal().equals(_other.quantity2.toBigDecimal());
    }

    /**
     * @deprecated This API is ICU internal only.
     * @hide draft / provisional / internal are hidden on Android
     */
    @Deprecated
    public IFixedDecimal getFirstFixedDecimal() {
        return quantity1;
    }

    /**
     * @deprecated This API is ICU internal only.
     * @hide draft / provisional / internal are hidden on Android
     */
    @Deprecated
    public IFixedDecimal getSecondFixedDecimal() {
        return quantity2;
    }
}
