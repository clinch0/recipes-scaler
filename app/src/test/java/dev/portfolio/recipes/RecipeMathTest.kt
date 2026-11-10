package dev.portfolio.recipes
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
class RecipeMathTest {
 @Test fun integerScale()=assertEquals("3200",formatGrams(scaled(BigDecimal("320"),10)))
 @Test fun decimalScale()=assertEquals("2,2",formatGrams(scaled(BigDecimal("1.1"),2)))
 @Test fun zeroPortions()=assertEquals("0",formatGrams(scaled(BigDecimal("7.5"),0)))
 @Test fun largePortions()=assertEquals("999999999000",formatGrams(scaled(BigDecimal("999999999"),1000)))
}
