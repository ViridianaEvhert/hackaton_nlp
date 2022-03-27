# nlp_hackathon

coleccion de sctipts para el hackathon de NLP en espaniol

## System setup

Estos son los pasos a seguir:

1. Asegurarte de tener instalada una version *reciente* de python (i.e., 3.6, 3.8)
2. Clonar el repo
    - `git clone git@github.com:ViridianaEvhert/hackaton_nlp.git`
3. Abrir terminal dentro del directorio repo recien clonado
4. Crear ambiente virtual:
    - `python3.6 -m venv venv`
5. Activar ambiente virtual:
    - En una terminal windows (`cmd`, `powershell`): `source venv/Scripts/activate` + enter
    - En una terminal linux: `source venv/bin/activate` + enter
5. Actualizar `pip`
    - `python -m pip install --upgrade pip`
6. Instalar paquetes especificados en `requiremtents.txt`
    - `python -m pip install -r requirements.txt`
7. Si depues de este paso necesitas instalar alguna libreria adicional, agregala
   a `requirements.txt` y repite el paso anterior

Para bajar las transcripciones de las manianeras del portal de la [presidencia][2]
ejecuta `hackathon_nlp/src/py3_collect_data.py`

Si desear utilizar el script para la recoleccion de resumenes de youtube
(nlp_hackathon/src/py3_query_youtube_info.py), es necesario crear una
`google account`, loggearse y settear los permisos necesarios como se describe
[aqui][1]. La API key debe ser pasada como el argumento `key` al inicializar
objetos de la clase `ParseChannelVideos` en
`hackathon_nlp/src/py3_query_youtube_info.py`


[1]: https://medium.com/mcd-unison/youtube-data-api-v3-in-python-tutorial-with-examples-e829a25d2ebd
[2]: https://www.gob.mx/presidencia/es/archivo/articulos?filter_id=&filter_origin=archive&idiom=es&order=DESC&page=1&style=list&tags=&utf8=%E2%9C%93