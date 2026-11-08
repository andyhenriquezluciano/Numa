# Cuenta de NÜMA

Servicio de Cloudflare Workers para el acceso con Google, dispositivos y sincronización
de listas, favoritos, artistas, álbumes, carátulas y métricas entre Android y Windows.
Los archivos de audio y las sesiones de proveedores permanecen en cada dispositivo.

## Configuración

1. Ejecutar `npm ci`.
2. Copiar `wrangler.numa.example.toml` y configurar el Worker, la base D1 y los bindings.
3. Crear un cliente OAuth web de Google con la redirección
   `https://<worker>/auth/google/callback` y configurar `GOOGLE_CLIENT_ID`.
4. Cargar `GOOGLE_CLIENT_SECRET` y `SIGNING_KEY` con `wrangler secret put`.
   `npm run generate-key` genera la clave de firma.
5. Aplicar las migraciones y desplegar con la configuración de producción.

La configuración privada está en `wrangler.production.jsonc` y no se versiona.
La instancia de NÜMA usa `https://numa-account.peluchito281.workers.dev`.

## Validación

`npm test` comprueba autenticación, sincronización, permisos y almacenamiento.
El acceso usa PKCE, tokens de corta duración y renovación por dispositivo.
Los secretos se guardan en Cloudflare y nunca se incluyen en las aplicaciones.

Los módulos opcionales de informes, Discord y Connect requieren sus bindings o
credenciales indicados en la configuración de ejemplo. No son necesarios para
sincronizar la biblioteca.

## Llamadas e invocaciones

Una petición HTTP que llega a `numa-account` sigue siendo una invocación aunque
responda `304`. Las lecturas de D1 y los envíos al Durable Object son trabajo
interno; reducirlos no equivale a eliminar la petición inicial del cliente.

El cliente Windows agrupa cambios de biblioteca durante 2 segundos. El tiempo
escuchado se acumula localmente y se sincroniza cada 5 minutos mientras hay
actividad. Un temporizador de respaldo consulta a los 15 minutos de la última
sincronización y los errores usan espera progresiva de 30 segundos a 15 minutos.
Estos temporizadores son de la aplicación, no tareas programadas del Worker.

| Operación | Comportamiento |
| --- | --- |
| Abrir Cuentas | Una petición `/devices` compartida si ya está en curso; reutiliza el resultado correcto durante 30 segundos. |
| Quitar un dispositivo | `DELETE /devices/<id>` y una lectura forzada de la lista, incluso si existía caché. Cerrar sesión o cambiar de cuenta invalida esa caché. |
| Guardar favoritos, listas o métricas | Un `POST /sync/library` con los cambios agrupados; el Durable Object avisa directamente por WebSocket, sin hacerse otra petición a sí mismo. |
| Confirmar solo una secuencia, sin cambiar la biblioteca | Guarda la confirmación necesaria, pero no despierta a otros dispositivos con un aviso sin novedades. |
| Aviso `library.changed` | El cliente consulta si la revisión recibida es más nueva que la que ya conoce. |
| Consultar una biblioteca sin cambios | Envía `If-None-Match`; el servidor consulta la revisión y responde `304` sin volver a enviar la biblioteca completa. |
| Varias lecturas simultáneas iguales | El Durable Object comparte únicamente la lectura de biblioteca que está en curso. La siguiente consulta vuelve a leer D1; no hay una copia persistente que pueda quedar obsoleta. |
| Carátulas privadas | Se descargan cuando falta su copia local y se verifican mediante su hash. |

La sesión y la existencia del dispositivo se verifican en cada petición, incluso
cuando la respuesta es `304`. La caché de la lista de dispositivos es solo una
optimización de la interfaz; no autoriza peticiones ni retrasa una revocación.
Los clientes anteriores que no envían `If-None-Match` siguen recibiendo la
respuesta completa habitual. Los cambios del cliente requieren distribuir un
nuevo ejecutable; los del Worker son compatibles con las aplicaciones instaladas.
