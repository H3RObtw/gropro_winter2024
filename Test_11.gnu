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
0 200 100 300 "Obj A" 1
0 0 200 200 "Obj B" 2
200 0 500 150 "Obj C" 3
500 0 650 300 "Obj D" 4
650 0 900 250 "Obj E" 5
200 150 300 200 "Obj F" 6
300 150 400 200 "Obj G" 7
100 200 500 300 "Obj H" 8
0 300 400 400 "Obj I" 9
400 300 697 510 "Obj J" 10
0 400 297 610 "Obj K" 11
400 510 550 660 "Obj L" 12
297 400 377 600 "Obj M" 13
0 610 200 690 "Obj N" 14
697 300 797 650 "Obj O" 15
797 300 897 650 "Obj P" 16
0 880 220 1100 "Obj Q" 17
310 690 510 1110 "Obj R" 18
510 690 710 1110 "Obj S" 19
710 690 890 750 "Obj T" 20
220 880 280 1060 "Obj U" 21
0 690 310 880 "Obj V" 22
710 750 900 1060 "Obj W" 23
710 1060 810 1160 "Obj X" 24
0 1160 150 1310 "Obj Y" 25
150 1160 350 1360 "Obj Z" 26
350 1160 600 1410 "Obj AA" 27
600 1160 900 1310 "Obj BB" 28
600 1310 900 1460 "Obj CC" 29
0 1310 50 1410 "Obj DD" 30
50 1310 150 1360 "Obj EE" 31
0 1410 400 1510 "Obj FF" 32
0 1510 400 1610 "Obj GG" 33
400 1510 697 1720 "Obj HH" 34
0 1610 297 1820 "Obj II" 35
400 1720 550 1870 "Obj JJ" 36
297 1610 377 1810 "Obj KK" 37
0 1820 200 1900 "Obj LL" 38
697 1510 797 1860 "Obj MM" 39
797 1510 897 1860 "Obj NN" 40
0 1900 220 2120 "Obj OO" 41
220 1900 640 2100 "Obj PP" 42
220 2100 640 2300 "Obj QQ" 43
640 1900 700 2080 "Obj RR" 44
700 1900 880 1960 "Obj SS" 45
EOD

$anchor <<EOD
900 0
400 150
650 250
897 300
377 400
550 510
297 600
200 610
697 650
797 650
400 660
890 690
900 750
280 880
220 1060
810 1060
0 1100
310 1110
510 1110
900 1160
900 1310
50 1360
150 1360
400 1410
600 1460
897 1510
377 1610
550 1720
297 1810
200 1820
697 1860
797 1860
400 1870
880 1900
700 1960
640 2080
640 2100
0 2120
220 2300
EOD

plot \
'$data' using (($3-$1)/2+$1):(($4-$2)/2+$2):(($3-$1)/2):(($4-$2)/2):6 with boxxy linecolor var, \
'$data' using (($3-$1)/2+$1):(($4-$2)/2+$2):5 with labels font "arial,9", \
'$anchor' using 1:2 with circles lc rgb "red", \
'$data' using 1:2 with points lw 8 lc rgb "dark-green"
