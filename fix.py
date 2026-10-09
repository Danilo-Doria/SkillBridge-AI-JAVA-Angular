import os
with open("frontend/src/app/features/payment-modal/payment-modal.html", "r", encoding="utf-8") as f:
    html = f.read()
idx = html.find("<!-- Amount (readonly) -->")
endIdx = html.find("<!-- Card Number -->")
fixed = """<!-- Amount (readonly) -->
        <div>
          <label class="block text-sm font-medium text-slate-700">Monto a pagar</label>
          <div class="mt-1">
            <input 
              type="text" 
              disabled 
              [value]="\x27$\x27 + (amount | number)" 
              class="block w-full rounded-lg border-slate-300 bg-slate-100 py-2.5 px-3 text-slate-700 sm:text-sm"
            >
          </div>
        </div>

        """
html = html[:idx] + fixed + html[endIdx:]
with open("frontend/src/app/features/payment-modal/payment-modal.html", "w", encoding="utf-8") as f:
    f.write(html)

