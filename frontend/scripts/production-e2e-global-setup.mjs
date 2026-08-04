import { server, startProductionBuildServer } from './serve-production-build.mjs';

export default async function productionE2eGlobalSetup() {
  await startProductionBuildServer();

  return () =>
    new Promise((resolve, reject) => {
      server.close((error) => {
        if (error) {
          reject(error);
          return;
        }
        resolve();
      });
    });
}
