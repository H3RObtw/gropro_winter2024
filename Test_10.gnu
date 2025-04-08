reset
set term png size 900,1840
set output 'Test_10.png'
set xrange [0:900]
set yrange [0:1740]
set size ratio -1

set title "\
// Auftrag Test 10 - Very Large set (40), low depth crucial\n\
Benötigte Länge: 174.0cm\n\
Genutzte Fläche: 80.30%"

set style fill transparent solid 0.5 border
set key noautotitle

$data <<EOD
80 0 160 120 "Job A" 1
0 0 80 120 "Job B" 2
160 0 260 100 "Job C" 3
260 0 410 150 "Job D" 4
410 0 610 100 "Job E" 5
610 0 810 100 "Job F" 6
300 150 550 230 "Job G" 7
300 230 550 310 "Job H" 8
0 150 300 250 "Job I" 9
0 250 300 350 "Job J" 10
550 150 730 330 "Job K" 11
730 150 880 370 "Job L" 12
297 370 517 520 "Job M" 13
0 580 400 670 "Job N" 14
400 580 800 670 "Job O" 15
0 370 297 580 "Job P" 16
517 370 814 580 "Job Q" 17
814 370 864 420 "Job R" 18
350 670 480 800 "Job S" 19
0 790 350 910 "Job T" 20
0 670 350 790 "Job U" 21
480 670 720 910 "Job V" 22
0 910 450 1010 "Job W" 23
450 910 900 1010 "Job X" 24
310 1010 470 1210 "Job Y" 25
470 1010 670 1170 "Job Z" 26
0 1010 310 1120 "Job AA" 27
0 1120 310 1230 "Job BB" 28
670 1010 760 1160 "Job CC" 29
760 1010 850 1160 "Job DD" 30
140 1230 560 1330 "Job EE" 31
140 1330 560 1430 "Job FF" 32
560 1230 760 1430 "Job GG" 33
0 1230 140 1510 "Job HH" 34
760 1230 900 1510 "Job II" 35
140 1430 440 1500 "Job JJ" 36
190 1510 490 1580 "Job KK" 37
0 1510 190 1700 "Job LL" 38
190 1580 690 1660 "Job MM" 39
190 1660 690 1740 "Job NN" 40
EOD

$anchor <<EOD
810 0
160 100
410 100
610 100
0 120
80 120
880 150
300 310
550 330
0 350
864 370
814 420
297 520
800 580
720 670
350 800
850 1010
670 1160
760 1160
470 1170
310 1210
440 1430
560 1430
140 1500
490 1510
760 1510
690 1580
690 1660
0 1700
190 1740
EOD

plot \
'$data' using (($3-$1)/2+$1):(($4-$2)/2+$2):(($3-$1)/2):(($4-$2)/2):6 with boxxy linecolor var, \
'$data' using (($3-$1)/2+$1):(($4-$2)/2+$2):5 with labels font "arial,9", \
'$anchor' using 1:2 with circles lc rgb "red", \
'$data' using 1:2 with points lw 8 lc rgb "dark-green"
