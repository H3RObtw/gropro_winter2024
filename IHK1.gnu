reset
set term png size 900,397
set output 'IHK1.png'
set xrange [0:900]
set yrange [0:297]
set size ratio -1

set title "\
// Auftrag IHK1\n\
Benötigte Länge: 29.7cm\n\
Genutzte Fläche: 54.15%"

set style fill transparent solid 0.5 border
set key noautotitle

$data <<EOD
0 0 100 200 "Auftrag A" 1
100 0 520 297 "Auftrag B" 2
EOD

$anchor <<EOD
520 0
0 200
100 297
EOD

plot \
'$data' using (($3-$1)/2+$1):(($4-$2)/2+$2):(($3-$1)/2):(($4-$2)/2):6 with boxxy linecolor var, \
'$data' using (($3-$1)/2+$1):(($4-$2)/2+$2):5 with labels font "arial,9", \
'$anchor' using 1:2 with circles lc rgb "red", \
'$data' using 1:2 with points lw 8 lc rgb "dark-green"
