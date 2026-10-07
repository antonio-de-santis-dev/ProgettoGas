#!/usr/bin/env python3
"""Verify all source scenarios, exports and persistence; no personal input data."""
import json,sys,os
from urllib.request import Request,urlopen
from pathlib import Path
from decimal import Decimal
base=os.environ.get('GAS_TEST_URL','http://127.0.0.1:8090')
def call(path,data=None,method=None):
    r=Request(base+'/api'+path,data=json.dumps(data).encode() if data is not None else None,headers={'Content-Type':'application/json'},method=method)
    with urlopen(r,timeout=60) as response:return json.load(response)
state=Path(os.environ.get('GAS_TEST_STATE','/tmp/progettogas-ci-state.json'))
if '--persistenza' in sys.argv:
    expected=json.loads(state.read_text())
    for s in expected['confronti']:
        actual=call('/confronti/'+str(s['id']))
        assert actual==s,'Snapshot cambiato dopo il riavvio'
    assert call('/impostazioni-pdf')==expected['pdf']
    print('Persistenza dati, confronti e personalizzazione PDF verificata.')
else:
    comparisons=[]
    for example in call('/esempi'):
        data=call('/esempi/'+example['name'],{},'POST');c=data['comparison'];comparisons.append(c)
        assert abs(Decimal(c['data']['result']['totalRaw'])-Decimal(example['expectedRaw']))<=Decimal('0.000000001')
        with urlopen(base+'/api/confronti/'+str(c['id'])+'/pdf') as response:assert response.read(5)==b'%PDF-'
        with urlopen(base+'/api/confronti/'+str(c['id'])+'/export?format=csv') as response:assert b'UG2_FIXED' in response.read()
    options={'stile':'SINTESI','colore':'#1E3A8A','logo':None,'consulente':{'nome':'Studio Gas CI','ruolo':'Consulente','email':'test@example.com','telefono':'','indirizzo':'','dimostrativo':False}}
    settings=call('/impostazioni-pdf');settings=call('/impostazioni-pdf',{'opzioni':options,'versione':settings['versione']},'PUT')
    assert call('/impostazioni-pdf/anteprima',options,'POST')['pagine']
    state.write_text(json.dumps({'confronti':comparisons,'pdf':settings}))
    print('Quattro regressioni Excel, PDF, CSV e impostazioni verificati.')
