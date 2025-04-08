reset
set term png size 900,1570
set output 'Test_8.png'
set xrange [0:900]
set yrange [0:1470]
set size ratio -1

set title "\
// Auftrag Test 8 - Large set (25), lower depth for speed\n\
Benötigte Länge: 147.0cm\n\
Genutzte Fläche: 82.66%"

set style fill transparent solid 0.5 border
set key noautotitle

$data <<EOD
550 150 650 250 "Ord A" 1
550 0 750 150 "Ord B" 2
750 0 900 200 "Ord C" 3
0 100 300 200 "Ord D" 4
0 0 300 100 "Ord E" 5
300 0 550 250 "Ord F" 6
0 460 400 510 "Ord G" 7
400 460 800 510 "Ord H" 8
0 250 297 460 "Ord I" 9
297 250 594 460 "Ord J" 10
594 250 714 430 "Ord K" 11
714 250 894 370 "Ord L" 12
670 510 780 840 "Ord M" 13
780 510 890 840 "Ord N" 14
450 510 670 730 "Ord O" 15
450 730 530 810 "Ord P" 16
0 510 450 660 "Ord Q" 17
0 660 450 810 "Ord R" 18
0 940 600 1040 "Ord S" 19
0 840 600 940 "Ord T" 20
600 840 870 1110 "Ord U" 21
0 1160 310 1350 "Ord V" 22
310 1160 620 1350 "Ord W" 23
0 1040 500 1160 "Ord X" 24
0 1350 500 1470 "Ord Y" 25
EOD

$anchor <<EOD
650 150
0 200
750 200
894 250
714 370
594 430
800 460
890 510
530 730
0 810
450 810
870 840
500 1040
600 1110
620 1160
500 1350
0 1470
EOD

plot \
'$data' using (($3-$1)/2+$1):(($4-$2)/2+$2):(($3-$1)/2):(($4-$2)/2):6 with boxxy linecolor var, \
'$data' using (($3-$1)/2+$1):(($4-$2)/2+$2):5 with labels font "arial,9", \
'$anchor' using 1:2 with circles lc rgb "red", \
'$data' using 1:2 with points lw 8 lc rgb "dark-green"
