# Integración opcional de Last.fm

Last.fm es un servicio externo para registrar escuchas. Esta integración está
inactiva por defecto y no afecta a las métricas propias de NÜMA.

Para habilitarla en un despliegue propio:

1. Registrar una aplicación en Last.fm y obtener sus credenciales.
2. Configurar un Worker a partir de `wrangler.jsonc`.
3. Cargar `LASTFM_API_KEY` y `LASTFM_SHARED_SECRET` mediante `wrangler secret put`.
4. Ejecutar `npm ci`, `npm test` y desplegar la configuración propia.
5. Definir `NUMA_LASTFM_URL` al iniciar Windows o `-PnumaLastfmUrl=https://<worker>`
   al compilar Android.

Las claves no se distribuyen con los clientes. Sin una URL configurada no se
restauran sesiones ni se realizan solicitudes a Last.fm.
