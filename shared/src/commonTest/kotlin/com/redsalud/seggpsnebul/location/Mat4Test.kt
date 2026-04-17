package com.redsalud.seggpsnebul.location

import kotlin.test.*

class Mat4Test {

    @Test
    fun diagonal_createsCorrectMatrix() {
        val m = Mat4.diagonal(1.0, 2.0, 3.0, 4.0)
        assertEquals(1.0, m[0, 0])
        assertEquals(2.0, m[1, 1])
        assertEquals(3.0, m[2, 2])
        assertEquals(4.0, m[3, 3])
        // off-diagonals are zero
        assertEquals(0.0, m[0, 1])
        assertEquals(0.0, m[1, 0])
        assertEquals(0.0, m[2, 3])
    }

    @Test
    fun of_populatesRowMajor() {
        val m = Mat4.of(
            1.0, 2.0, 3.0, 4.0,
            5.0, 6.0, 7.0, 8.0,
            9.0, 10.0, 11.0, 12.0,
            13.0, 14.0, 15.0, 16.0
        )
        assertEquals(1.0, m[0, 0])
        assertEquals(4.0, m[0, 3])
        assertEquals(5.0, m[1, 0])
        assertEquals(16.0, m[3, 3])
    }

    @Test
    fun plus_addsElementwise() {
        val a = Mat4.diagonal(1.0, 2.0, 3.0, 4.0)
        val b = Mat4.diagonal(10.0, 20.0, 30.0, 40.0)
        val c = a + b
        assertEquals(11.0, c[0, 0])
        assertEquals(22.0, c[1, 1])
        assertEquals(33.0, c[2, 2])
        assertEquals(44.0, c[3, 3])
    }

    @Test
    fun times_identityMatrix_returnsOriginal() {
        val identity = Mat4.diagonal(1.0, 1.0, 1.0, 1.0)
        val m = Mat4.of(
            1.0, 2.0, 3.0, 4.0,
            5.0, 6.0, 7.0, 8.0,
            9.0, 10.0, 11.0, 12.0,
            13.0, 14.0, 15.0, 16.0
        )
        val result = identity * m
        for (r in 0..3) for (c in 0..3) {
            assertEquals(m[r, c], result[r, c], "Mismatch at [$r,$c]")
        }
    }

    @Test
    fun times_diagonals_multiplyElementwise() {
        val a = Mat4.diagonal(2.0, 3.0, 4.0, 5.0)
        val b = Mat4.diagonal(10.0, 20.0, 30.0, 40.0)
        val c = a * b
        assertEquals(20.0, c[0, 0])
        assertEquals(60.0, c[1, 1])
        assertEquals(120.0, c[2, 2])
        assertEquals(200.0, c[3, 3])
        assertEquals(0.0, c[0, 1])
    }

    @Test
    fun transpose_swapsRowsAndColumns() {
        val m = Mat4.of(
            1.0, 2.0, 3.0, 4.0,
            5.0, 6.0, 7.0, 8.0,
            9.0, 10.0, 11.0, 12.0,
            13.0, 14.0, 15.0, 16.0
        )
        val t = m.T
        assertEquals(1.0, t[0, 0])
        assertEquals(5.0, t[0, 1])
        assertEquals(9.0, t[0, 2])
        assertEquals(13.0, t[0, 3])
        assertEquals(2.0, t[1, 0])
    }

    @Test
    fun transpose_ofDiagonal_isSameMatrix() {
        val d = Mat4.diagonal(1.0, 2.0, 3.0, 4.0)
        val t = d.T
        for (r in 0..3) for (c in 0..3) {
            assertEquals(d[r, c], t[r, c])
        }
    }

    @Test
    fun fPfT_plus_Q_ekfCovariancePropagation() {
        // Simulates P_new = F * P * F^T + Q for dt=1
        val F = Mat4.of(
            1.0, 0.0, 1.0, 0.0,
            0.0, 1.0, 0.0, 1.0,
            0.0, 0.0, 1.0, 0.0,
            0.0, 0.0, 0.0, 1.0
        )
        val P = Mat4.diagonal(10.0, 10.0, 1.0, 1.0)
        val Q = Mat4.diagonal(0.5, 0.5, 2.0, 2.0)

        val result = F * P * F.T + Q
        // P_new[0,0] = P[0,0] + 2*P[0,2]*dt + P[2,2]*dt^2 + Q[0,0]
        // = 10 + 0 + 1 + 0.5 = 11.5
        assertEquals(11.5, result[0, 0], 1e-10)
        assertEquals(11.5, result[1, 1], 1e-10)
    }
}
