reset
set term png size 900,2400
set output 'Test_11.png'
set xrange [0:900]
set yrange [0:2300]
set size ratio -1

set title "\
// Auftrag Test 11 - Very Large set (45), medium depth\n\
Benötigte Länge: 230.0cm\n\
Genutzte Fläche: 82.11%"

set style fill transparent solid 0.5 border
set key noautotitle

$data <<EOD
400 0 500 100 "Obj A" 1
0 100 200 300 "Obj B" 2
500 0 650 300 "Obj C" 3
200 100 500 250 "Obj D" 4
650 0 900 250 "Obj E" 5
200 250 300 300 "Obj F" 6
300 250 400 300 "Obj G" 7
0 0 400 100 "Obj H" 8
297 300 697 400 "Obj I" 9
297 400 594 610 "Obj J" 10
0 300 297 510 "Obj K" 11
0 510 150 660 "Obj L" 12
594 400 674 600 "Obj M" 13
297 610 497 690 "Obj N" 14
697 300 797 650 "Obj O" 15
797 300 897 650 "Obj P" 16
0 880 220 1100 "Obj Q" 17
310 690 510 1110 "Obj R" 18
510 690 710 1110 "Obj S" 19
710 690 890 750 "Obj T" 20
220 880 280 1060 "Obj U" 21
710 750 900 1060 "Obj V" 22
0 690 310 880 "Obj W" 23
710 1060 810 1160 "Obj X" 24
150 1160 300 1310 "Obj Y" 25
300 1160 500 1360 "Obj Z" 26
500 1160 750 1410 "Obj AA" 27
0 1160 150 1460 "Obj BB" 28
750 1160 900 1460 "Obj CC" 29
150 1310 200 1410 "Obj DD" 30
200 1310 300 1360 "Obj EE" 31
150 1410 550 1510 "Obj FF" 32
297 1510 697 1610 "Obj GG" 33
0 1510 297 1720 "Obj HH" 34
297 1610 594 1820 "Obj II" 35
0 1720 150 1870 "Obj JJ" 36
594 1610 674 1810 "Obj KK" 37
297 1820 497 1900 "Obj LL" 38
697 1510 797 1860 "Obj MM" 39
797 1510 897 1860 "Obj NN" 40
420 1900 640 2120 "Obj OO" 41
0 1900 420 2100 "Obj PP" 42
0 2100 420 2300 "Obj QQ" 43
640 1900 700 2080 "Obj RR" 44
700 1900 880 1960 "Obj SS" 45
EOD

$anchor <<EOD
400 250
650 250
897 300
674 400
150 510
594 600
497 610
697 650
797 650
0 660
890 690
280 880
220 1060
810 1060
0 1100
310 1110
510 1110
200 1360
300 1360
550 1410
0 1460
750 1460
897 1510
674 1610
150 1720
594 1810
497 1820
697 1860
797 1860
0 1870
880 1900
700 1960
640 2080
420 2120
0 2300
EOD

plot \
'$data' using (($3-$1)/2+$1):(($4-$2)/2+$2):(($3-$1)/2):(($4-$2)/2):6 with boxxy linecolor var, \
'$data' using (($3-$1)/2+$1):(($4-$2)/2+$2):5 with labels font "arial,9", \
'$anchor' using 1:2 with circles lc rgb "red", \
'$data' using 1:2 with points lw 8 lc rgb "dark-green"
