#!/usr/bin/env bash
# measure.sh — mide las cifras de HU-104 (NFR-002/003/004, SC-003). No modifica nada del stack.
# Uso:
#   measure.sh ram     <label> [minutos=10] [intervalo_s=15]   # RAM por contenedor (docker stats)
#   measure.sh latency <label> <url> [n=300] [concurrencia=5]  # p50/p95/p99 con curl (o hey si existe)
#   measure.sh startup <label> <contenedor> <url_health> [timeout_s=60]  # reinicia y mide tiempo hasta healthy
#   measure.sh compare <latencyA.csv> <latencyB.csv>           # sobrecarga del gateway (p95 B - p95 A)
# Salida: measure/results/<UTC>-<label>-<modo>.csv  (los resultados se pegan luego en progress.md)
set -Eeuo pipefail
OUT_DIR="$(cd "$(dirname "$0")" && pwd)/results"; mkdir -p "$OUT_DIR"
TS="$(date -u +%Y%m%dT%H%M%SZ)"
to_mib() { awk -v v="$1" 'BEGIN{u=v; gsub(/[0-9.]/,"",u); n=v+0;
  if(u=="GiB")n*=1024; else if(u=="KiB")n/=1024; else if(u=="B")n/=1048576; printf "%.1f",n}'; }

case "${1:-}" in
ram)
  label=${2:?label}; mins=${3:-10}; every=${4:-15}
  f="$OUT_DIR/$TS-$label-ram.csv"; echo "t_s,container,mem_mib" > "$f"
  end=$(( $(date +%s) + mins*60 )); start=$(date +%s)
  while [ "$(date +%s)" -lt "$end" ]; do
    t=$(( $(date +%s) - start ))
    docker stats --no-stream --format '{{.Name}} {{.MemUsage}}' | while read -r name usage _; do
      echo "$t,$name,$(to_mib "$usage")" >> "$f"; done
    sleep "$every"
  done
  echo "== Último muestreo y pico por contenedor (MiB) =="
  awk -F, 'NR>1{ if($3>max[$2])max[$2]=$3; last[$2]=$3 }
    END{ for(c in max){ printf "%-30s last=%8.1f peak=%8.1f\n",c,last[c],max[c]; tl+=last[c]; tp+=max[c] }
         printf "%-30s last=%8.1f peak=%8.1f\n","TOTAL (suma)",tl,tp }' "$f" | sort
  echo "Meta: TOTAL ≤ 8192 MiB; servicio Spring native ≤ 200 MiB en reposo. CSV: $f" ;;
latency)
  label=${2:?label}; url=${3:?url}; n=${4:-300}; c=${5:-5}
  f="$OUT_DIR/$TS-$label-latency.csv"; echo "ms" > "$f"
  # calentamiento (descartado)
  for _ in 1 2 3 4 5; do curl -ks -o /dev/null "$url" || true; done
  export url; seq "$n" | xargs -P "$c" -I{} curl -ks -o /dev/null -w '%{time_total}\n' "$url" \
    | awk '{printf "%.2f\n",$1*1000}' >> "$f"
  sort -n "$f" | awk 'NR>1{a[NR-1]=$1} END{n=NR-1;
    printf "n=%d p50=%.1f p95=%.1f p99=%.1f max=%.1f (ms)\n",n,a[int(n*0.50)],a[int(n*0.95)],a[int(n*0.99)],a[n]}'
  echo "CSV: $f" ;;
startup)
  label=${2:?label}; ctr=${3:?contenedor}; url=${4:?url_health}; to=${5:-60}
  f="$OUT_DIR/$TS-$label-startup.csv"
  docker restart "$ctr" >/dev/null; s=$(date +%s.%N)
  until curl -ksf -o /dev/null "$url"; do
    [ "$(awk -v s="$s" -v n="$(date +%s.%N)" 'BEGIN{print (n-s>'"$to"')}')" = 1 ] && { echo "TIMEOUT $to s"; exit 1; }
    sleep 0.1; done
  d=$(awk -v s="$s" -v n="$(date +%s.%N)" 'BEGIN{printf "%.2f",n-s}')
  echo "container,seconds" > "$f"; echo "$ctr,$d" >> "$f"; echo "$ctr listo en ${d}s (meta native < 3 s). CSV: $f" ;;
compare)
  a=${2:?csvA}; b=${3:?csvB}
  p95() { sort -n "$1" | awk 'NR>1{a[NR-1]=$1} END{n=NR-1; print a[int(n*0.95)]}'; }
  pa=$(p95 "$a"); pb=$(p95 "$b")
  awk -v a="$pa" -v b="$pb" 'BEGIN{printf "p95 A=%.1f ms, p95 B=%.1f ms, sobrecarga=%.1f ms (meta < 20 ms)\n",a,b,b-a}' ;;
*) sed -n '2,10p' "$0"; exit 2 ;;
esac
