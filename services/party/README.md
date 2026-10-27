# Salas Compartidas

Worker de Cloudflare y Durable Objects para crear salas, autenticar participantes
y transmitir el estado de reproducción entre Android y Windows. El audio permanece
en cada dispositivo. Los invitados solo pueden consultar el reloj; el anfitrión
controla la reproducción y puede transferir el control.

Configura `PUBLIC_URL` en `wrangler.toml` con tu dirección de Workers y ejecuta:

```sh
npm ci
npm test
npm run deploy
```

`POST /rooms` crea una sala. `POST /rooms/CODIGO/join` añade un participante.
La respuesta contiene un token individual para el WebSocket de la sala.
Las salas caducan a las seis horas y admiten hasta doce participantes.
