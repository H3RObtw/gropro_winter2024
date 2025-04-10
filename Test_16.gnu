reset
set term png size 900,4958
set output 'Test_16.png'
set xrange [0:900]
set yrange [0:4858]
set size ratio -1

set title "\
// Auftrag Test 15 - 100 Orders, Depth 15, Target >=95% Utilisation\n\
Benötigte Länge: 485.8cm\n\
Genutzte Fläche: 96.51%"

set style fill transparent solid 0.5 border
set key noautotitle

$data <<EOD
600 650 900 750 "W300 H100 A" 1
0 500 300 650 "W300 H150 B" 2
600 250 900 450 "W300 H200 C" 3
300 0 600 250 "W300 H250 D" 4
600 750 900 850 "W300 H100 E" 5
0 0 300 300 "W300 H300 F" 6
0 650 300 800 "W300 H150 G" 7
0 300 300 500 "W300 H200 H" 8
300 470 600 650 "W300 H180 I" 9
300 250 600 470 "W300 H220 J" 10
0 800 300 900 "W300 H100 K" 11
300 650 600 800 "W300 H150 L" 12
600 450 900 650 "W300 H200 M" 13
600 0 900 250 "W300 H250 N" 14
300 800 600 900 "W300 H100 O" 15
450 1150 750 1450 "W300 H300 P" 16
0 1900 300 2050 "W300 H150 Q" 17
450 1670 750 1870 "W300 H200 R" 18
450 1870 750 2050 "W300 H180 S" 19
450 1450 750 1670 "W300 H220 T" 20
0 2050 450 2150 "W450 H100 A" 21
0 1200 450 1400 "W450 H200 B" 22
750 1150 900 1600 "W450 H150 C" 23
450 900 900 1150 "W450 H250 D" 24
450 2050 900 2150 "W450 H100 E" 25
0 900 450 1200 "W450 H300 F" 26
750 1600 900 2050 "W450 H150 G" 27
0 1400 450 1600 "W450 H200 H" 28
0 1780 450 1900 "W450 H120 I" 29
0 1600 450 1780 "W450 H180 J" 30
0 2400 450 2500 "W450 H100 K" 31
450 2150 900 2350 "W450 H200 L" 32
450 2350 900 2500 "W450 H150 M" 33
0 2150 450 2400 "W450 H250 N" 34
330 2650 430 2800 "W150 H100 A" 35
600 2500 750 2700 "W150 H200 B" 36
600 2700 750 2850 "W150 H150 C" 37
0 2500 150 2800 "W150 H300 D" 38
150 2750 300 2850 "W150 H100 E" 39
450 2500 600 2750 "W150 H250 F" 40
750 2700 900 2850 "W150 H150 G" 41
750 2500 900 2700 "W150 H200 H" 42
450 2750 600 2850 "W150 H100 I" 43
150 2500 450 2650 "W150 H300 J" 44
150 2650 330 2750 "W180 H100 A" 45
0 3030 150 3210 "W180 H150 B" 46
480 2850 680 3030 "W180 H200 C" 47
300 3100 480 3200 "W180 H100 D" 48
300 2850 480 3100 "W180 H250 E" 49
150 3030 300 3210 "W180 H150 F" 50
480 3030 680 3210 "W180 H200 G" 51
250 3210 430 3310 "W180 H100 H" 52
0 2850 300 3030 "W180 H300 I" 53
680 3150 860 3300 "W180 H150 J" 54
780 3050 880 3150 "W100 H100 A" 55
780 2850 880 3050 "W100 H200 B" 56
430 3210 580 3310 "W100 H150 C" 57
680 2850 780 3150 "W100 H300 D" 58
580 3210 680 3310 "W100 H100 E" 59
0 3210 250 3310 "W100 H250 F" 60
200 3410 300 3560 "W100 H150 G" 61
650 3310 850 3410 "W100 H200 H" 62
500 3410 600 3510 "W100 H100 I" 63
0 3310 350 3410 "W100 H350 J" 64
300 3410 400 3560 "W100 H150 K" 65
0 3410 200 3510 "W100 H200 L" 66
600 3410 700 3510 "W100 H100 M" 67
350 3310 650 3410 "W100 H300 N" 68
400 3410 500 3560 "W100 H150 O" 69
800 3490 850 3540 "Fill 50x50 A" 70
850 3310 900 3410 "Fill 50x100 B" 71
700 3490 800 3540 "Fill 100x50 C" 72
820 3410 900 3490 "Fill 80x80 D" 73
850 3490 900 3540 "Fill 50x50 E" 74
700 3410 820 3490 "Fill 120x80 F" 75
804 3560 884 3680 "Fill 80x120 G" 76
420 4060 570 4110 "Fill 50x150 H" 77
594 4067 744 4117 "Fill 150x50 I" 78
744 4067 794 4117 "Fill 50x50 J" 79
520 3980 580 4040 "Fill 60x60 K" 80
804 3680 884 3780 "Fill 100x80 L" 81
420 3980 520 4060 "Fill 80x100 M" 82
794 4067 844 4117 "Fill 50x50 N" 83
804 3780 894 3850 "Fill 70x90 O" 84
594 3560 804 3857 "Std A4P A" 85
594 3857 891 4067 "Std A4L B" 86
0 3560 297 3980 "Std A3P C" 87
297 3560 594 3980 "Std A3L D" 88
0 3980 210 4128 "Std A5P E" 89
210 3980 420 4128 "Std A5L F" 90
0 4128 297 4338 "Std A4P G" 91
400 4338 610 4635 "Std A4L H" 92
750 4638 898 4743 "Std A6P I" 93
750 4743 898 4848 "Std A6L J" 94
0 4638 400 4838 "Comp A1 400x200" 95
0 4338 400 4638 "Comp A2 400x300" 96
647 4128 897 4378 "Comp B1 250x250" 97
647 4378 897 4628 "Comp B2 250x250" 98
297 4128 647 4308 "Comp C1 350x180" 99
400 4638 750 4858 "Comp C2 350x220" 100
EOD

$anchor <<EOD
600 850
300 1900
430 2650
300 2750
0 2800
330 2800
880 2850
880 3050
860 3150
300 3200
680 3300
0 3510
500 3510
600 3510
700 3540
800 3540
850 3540
884 3560
884 3680
894 3780
804 3850
891 3857
580 3980
520 4040
570 4060
844 4067
420 4110
594 4117
744 4117
794 4117
897 4128
297 4308
610 4338
897 4378
647 4628
400 4635
898 4638
898 4743
0 4838
750 4848
400 4858
EOD

plot \
'$data' using (($3-$1)/2+$1):(($4-$2)/2+$2):(($3-$1)/2):(($4-$2)/2):6 with boxxy linecolor var, \
'$data' using (($3-$1)/2+$1):(($4-$2)/2+$2):5 with labels font "arial,9", \
'$anchor' using 1:2 with circles lc rgb "red", \
'$data' using 1:2 with points lw 8 lc rgb "dark-green"
