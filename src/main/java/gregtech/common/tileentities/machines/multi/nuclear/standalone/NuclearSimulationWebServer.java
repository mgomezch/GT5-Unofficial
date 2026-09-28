package gregtech.common.tileentities.machines.multi.nuclear.standalone;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import gregtech.common.tileentities.machines.multi.nuclear.NuclearSimulationEngine;

/**
 * Embedded standalone web server providing an interactive browser UI and REST API
 * for visual nuclear reactor design and real-time simulation.
 */
public class NuclearSimulationWebServer {

    private static StandaloneNuclearGrid grid;
    private static volatile boolean isRunning = false;
    private static ScheduledExecutorService ticker;
    private static int simDelayMs = 100;

    public static void startServer(int port) {
        try {
            grid = new StandaloneNuclearGrid(7, 7, NuclearSimulationEngine.PIPE_TIER_PLATINUM);
            grid.loadPreset("BREEDER_7X7");

            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

            server.createContext("/", new IndexHandler());
            server.createContext("/api/state", new StateHandler());
            server.createContext("/api/step", new StepHandler());
            server.createContext("/api/reset", new ResetHandler());
            server.createContext("/api/play", new PlayHandler());
            server.createContext("/api/load-preset", new PresetHandler());
            server.createContext("/api/set-tile", new SetTileHandler());
            server.createContext("/api/set-tier", new SetTierHandler());
            server.createContext("/api/set-turbine", new SetTurbineHandler());
            server.createContext("/api/set-params", new SetParamsHandler());

            server.setExecutor(Executors.newCachedThreadPool());
            server.start();

            // Background ticker loop
            ticker = Executors.newSingleThreadScheduledExecutor();
            ticker.scheduleAtFixedRate(() -> {
                if (isRunning && grid != null && !grid.isExploded()) {
                    grid.step();
                }
            }, 0, 50, TimeUnit.MILLISECONDS);

            System.out.println("\u001B[1;32m============================================================");
            System.out.println("  GTNH Nuclear Simulator Web Server ACTIVE");
            System.out.println("  Open in browser: http://localhost:" + port);
            System.out.println("============================================================\u001B[0m");

        } catch (IOException e) {
            System.err.println("Failed to start Web Server on port " + port + ": " + e.getMessage());
        }
    }

    private static void sendJsonResponse(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders()
            .set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders()
            .set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static Map<String, String> parseQueryParams(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isEmpty()) return map;
        for (String param : query.split("&")) {
            String[] entry = param.split("=");
            if (entry.length > 1) {
                map.put(
                    URLDecoder.decode(entry[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(entry[1], StandardCharsets.UTF_8));
            } else if (entry.length == 1) {
                map.put(URLDecoder.decode(entry[0], StandardCharsets.UTF_8), "");
            }
        }
        return map;
    }

    static class StateHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"width\":")
                .append(grid.getWidth())
                .append(",");
            sb.append("\"height\":")
                .append(grid.getHeight())
                .append(",");
            sb.append("\"pipeTier\":")
                .append(grid.getPipeTier())
                .append(",");
            sb.append("\"pipeTierName\":\"")
                .append(NuclearSimulationEngine.getPipeTierName(grid.getPipeTier()))
                .append("\",");
            sb.append("\"maxSafeTemp\":")
                .append(NuclearSimulationEngine.getMaxOperatingTemperature(grid.getPipeTier()))
                .append(",");
            sb.append("\"currentTick\":")
                .append(grid.getCurrentTick())
                .append(",");
            sb.append("\"isRunning\":")
                .append(isRunning)
                .append(",");
            sb.append("\"exploded\":")
                .append(grid.isExploded())
                .append(",");
            sb.append("\"explosionReason\":\"")
                .append(
                    grid.getExplosionReason()
                        .replace("\"", "\\\""))
                .append("\",");
            sb.append("\"coreMaxTemp\":")
                .append(String.format("%.2f", grid.getCoreMaxTemp()))
                .append(",");
            sb.append("\"coreAvgTemp\":")
                .append(String.format("%.2f", grid.getCoreAvgTemp()))
                .append(",");
            sb.append("\"efficiency\":")
                .append(String.format("%.3f", grid.getEfficiency()))
                .append(",");
            sb.append("\"lastNeutrons\":")
                .append(grid.getLastNeutronsProduced())
                .append(",");
            sb.append("\"fastAbsorbed\":")
                .append(grid.getLastFastAbsorbed())
                .append(",");
            sb.append("\"thermalAbsorbed\":")
                .append(grid.getLastThermalAbsorbed())
                .append(",");
            sb.append("\"escapedNeutrons\":")
                .append(grid.getLastEscapedNeutrons())
                .append(",");
            sb.append("\"totalNeutrons\":")
                .append(grid.getTotalNeutronsGenerated())
                .append(",");
            sb.append("\"totalSteam\":")
                .append(grid.getTotalSteamProduced())
                .append(",");
            sb.append("\"totalEU\":")
                .append(String.format("%.0f", grid.getTotalEnergyEU()))
                .append(",");
            sb.append("\"totalDeuterium\":")
                .append(grid.getTotalDeuteriumProduced())
                .append(",");
            sb.append("\"totalTritium\":")
                .append(grid.getTotalTritiumProduced())
                .append(",");

            sb.append("\"turbineMaterial\":\"")
                .append(
                    grid.getTurbineMaterial()
                        .name())
                .append("\",");
            sb.append("\"turbineMaterialName\":\"")
                .append(grid.getTurbineMaterial().displayName)
                .append("\",");
            sb.append("\"turbineSize\":\"")
                .append(
                    grid.getTurbineSize()
                        .name())
                .append("\",");
            sb.append("\"turbineSizeName\":\"")
                .append(grid.getTurbineSize().displayName)
                .append("\",");
            sb.append("\"turbineFitting\":\"")
                .append(
                    grid.getTurbineFitting()
                        .name())
                .append("\",");
            sb.append("\"turbineFittingName\":\"")
                .append(grid.getTurbineFitting().displayName)
                .append("\",");

            sb.append("\"flowRegularSteam\":")
                .append(String.format(java.util.Locale.US, "%.1f", grid.getFlowRegularSteam()))
                .append(",");
            sb.append("\"flowSuperheatedSteam\":")
                .append(String.format(java.util.Locale.US, "%.1f", grid.getFlowSuperheatedSteam()))
                .append(",");
            sb.append("\"flowSupercriticalSteam\":")
                .append(String.format(java.util.Locale.US, "%.1f", grid.getFlowSupercriticalSteam()))
                .append(",");
            sb.append("\"flowHeavyWaterSteam\":")
                .append(String.format(java.util.Locale.US, "%.1f", grid.getFlowHeavyWaterSteam()))
                .append(",");
            sb.append("\"flowHPHeavyWaterSteam\":")
                .append(String.format(java.util.Locale.US, "%.1f", grid.getFlowHPHeavyWaterSteam()))
                .append(",");
            sb.append("\"flowHotCoolant\":")
                .append(String.format(java.util.Locale.US, "%.1f", grid.getFlowHotCoolant()))
                .append(",");
            sb.append("\"flowDirectEU\":")
                .append(String.format(java.util.Locale.US, "%.1f", grid.getFlowDirectEU()))
                .append(",");

            TurbineCalculator.PowerEstimationResult p = grid.getLastPowerResult();
            sb.append("\"powerEstimate\":{");
            sb.append("\"totalPowerEUt\":")
                .append(String.format(java.util.Locale.US, "%.1f", p.totalPowerEUt))
                .append(",");
            sb.append("\"directPowerEUt\":")
                .append(String.format(java.util.Locale.US, "%.1f", p.directPowerEUt))
                .append(",");
            sb.append("\"xlstPowerEUt\":")
                .append(String.format(java.util.Locale.US, "%.1f", p.xlstPowerEUt))
                .append(",");
            sb.append("\"xlstHpPowerEUt\":")
                .append(String.format(java.util.Locale.US, "%.1f", p.xlstHpPowerEUt))
                .append(",");
            sb.append("\"xlstScPowerEUt\":")
                .append(String.format(java.util.Locale.US, "%.1f", p.xlstScPowerEUt))
                .append(",");
            sb.append("\"eheMode\":\"")
                .append(p.eheMode)
                .append("\",");
            sb.append("\"eheSteamProduced\":")
                .append(String.format(java.util.Locale.US, "%.1f", p.eheSteamProduced))
                .append(",");
            sb.append("\"eheDistilledWaterConsumed\":")
                .append(String.format(java.util.Locale.US, "%.1f", p.eheDistilledWaterConsumed))
                .append(",");
            sb.append("\"xlstTurbinesNeeded\":")
                .append(String.format(java.util.Locale.US, "%.2f", p.xlstTurbinesNeeded))
                .append(",");
            sb.append("\"xlstHpTurbinesNeeded\":")
                .append(String.format(java.util.Locale.US, "%.2f", p.xlstHpTurbinesNeeded))
                .append(",");
            sb.append("\"xlstScTurbinesNeeded\":")
                .append(String.format(java.util.Locale.US, "%.2f", p.xlstScTurbinesNeeded))
                .append(",");
            sb.append("\"efficiency\":")
                .append(String.format(java.util.Locale.US, "%.3f", p.efficiency))
                .append(",");
            sb.append("\"optFlowPerTurbine\":")
                .append(String.format(java.util.Locale.US, "%.0f", p.optFlowPerTurbine));
            sb.append("},");

            sb.append("\"params\":{");
            sb.append("\"hatchCapacity\":")
                .append(NuclearSimulationEngine.hatchCoolantCapacity)
                .append(",");
            sb.append("\"turnoverCurve\":\"")
                .append(NuclearSimulationEngine.turnoverCurve.name())
                .append("\",");
            sb.append("\"turnoverDeltaTMax\":")
                .append(String.format(java.util.Locale.US, "%.1f", NuclearSimulationEngine.turnoverDeltaTMax))
                .append(",");
            sb.append("\"turnoverExponent\":")
                .append(String.format(java.util.Locale.US, "%.2f", NuclearSimulationEngine.turnoverExponent))
                .append(",");
            sb.append("\"coolantFeedRate\":")
                .append(NuclearSimulationEngine.coolantFeedRate)
                .append(",");
            sb.append("\"coolingHeatPerLiter\":")
                .append(String.format(java.util.Locale.US, "%.1f", NuclearSimulationEngine.coolingHeatPerLiter))
                .append(",");
            sb.append("\"ambientTemp\":")
                .append(String.format(java.util.Locale.US, "%.1f", NuclearSimulationEngine.ambientTemp))
                .append(",");
            sb.append("\"ic2CoolantHeatPerLiter\":")
                .append(String.format(java.util.Locale.US, "%.1f", NuclearSimulationEngine.ic2CoolantHeatPerLiter))
                .append(",");
            sb.append("\"tempThresholdLow\":")
                .append(String.format(java.util.Locale.US, "%.1f", NuclearSimulationEngine.tempThresholdLow))
                .append(",");
            sb.append("\"tempThresholdHigh\":")
                .append(String.format(java.util.Locale.US, "%.1f", NuclearSimulationEngine.tempThresholdHigh))
                .append(",");
            sb.append("\"reactivityPower\":")
                .append(String.format(java.util.Locale.US, "%.2f", NuclearSimulationEngine.reactivityPower))
                .append(",");
            sb.append("\"thermalFissionMultiplier\":")
                .append(String.format(java.util.Locale.US, "%.2f", NuclearSimulationEngine.thermalFissionMultiplier))
                .append(",");
            sb.append("\"fissionHeatPerNeutron\":")
                .append(String.format(java.util.Locale.US, "%.1f", NuclearSimulationEngine.fissionHeatPerNeutron))
                .append(",");
            sb.append("\"hpWaterBoilingPoint\":")
                .append(String.format(java.util.Locale.US, "%.1f", NuclearSimulationEngine.hpWaterBoilingPoint));
            sb.append("},");

            // Tiles
            sb.append("\"tiles\":[");
            for (int y = 0; y < grid.getHeight(); y++) {
                for (int x = 0; x < grid.getWidth(); x++) {
                    SimTile t = grid.getTile(x, y);
                    if (x > 0 || y > 0) sb.append(",");
                    sb.append("{");
                    sb.append("\"x\":")
                        .append(x)
                        .append(",");
                    sb.append("\"y\":")
                        .append(y)
                        .append(",");
                    sb.append("\"type\":\"")
                        .append(
                            t.getType()
                                .name())
                        .append("\",");
                    sb.append("\"name\":\"")
                        .append(t.getType().displayName)
                        .append("\",");
                    sb.append("\"code\":\"")
                        .append(t.getType().code)
                        .append("\",");
                    sb.append("\"temp\":")
                        .append(String.format("%.1f", t.getTemperature()))
                        .append(",");
                    sb.append("\"isFuel\":")
                        .append(t.isFuel())
                        .append(",");
                    sb.append("\"durability\":")
                        .append(t.getDurability())
                        .append(",");
                    sb.append("\"durabilityPct\":")
                        .append(String.format("%.1f", t.getDurabilityPercent()))
                        .append(",");
                    sb.append("\"fluidName\":\"")
                        .append(t.getInputFluidName())
                        .append("\",");
                    sb.append("\"fluidAmount\":")
                        .append(t.getInputFluidAmount())
                        .append(",");
                    sb.append("\"fluidCapacity\":")
                        .append(t.getInputFluidCapacity())
                        .append(",");
                    sb.append("\"wasDry\":")
                        .append(t.isWasDry())
                        .append(",");
                    sb.append("\"lastProduced\":")
                        .append(t.getLastTickProduced())
                        .append(",");
                    sb.append("\"steamAmount\":")
                        .append(t.getTotalSteamProduced());
                    sb.append("}");
                }
            }
            sb.append("],");

            // Telemetry history
            sb.append("\"history\":[");
            List<StandaloneNuclearGrid.TickTelemetry> hist = grid.getHistory();
            int start = Math.max(0, hist.size() - 60);
            for (int i = start; i < hist.size(); i++) {
                if (i > start) sb.append(",");
                StandaloneNuclearGrid.TickTelemetry entry = hist.get(i);
                sb.append("{");
                sb.append("\"t\":")
                    .append(entry.tick())
                    .append(",");
                sb.append("\"maxT\":")
                    .append(String.format("%.1f", entry.maxTemp()))
                    .append(",");
                sb.append("\"avgT\":")
                    .append(String.format("%.1f", entry.avgTemp()))
                    .append(",");
                sb.append("\"eff\":")
                    .append(String.format("%.2f", entry.efficiency()))
                    .append(",");
                sb.append("\"power\":")
                    .append(String.format("%.1f", entry.powerEUt()))
                    .append(",");
                sb.append("\"safe\":")
                    .append(entry.safe());
                sb.append("}");
            }
            sb.append("]");

            sb.append("}");
            sendJsonResponse(exchange, 200, sb.toString());
        }
    }

    static class StepHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> params = parseQueryParams(
                exchange.getRequestURI()
                    .getQuery());
            int count = 1;
            if (params.containsKey("count")) {
                try {
                    count = Integer.parseInt(params.get("count"));
                } catch (NumberFormatException ignored) {}
            }
            for (int i = 0; i < count && !grid.isExploded(); i++) {
                grid.step();
            }
            sendJsonResponse(exchange, 200, "{\"success\":true,\"tick\":" + grid.getCurrentTick() + "}");
        }
    }

    static class PlayHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> params = parseQueryParams(
                exchange.getRequestURI()
                    .getQuery());
            if (params.containsKey("running")) {
                isRunning = Boolean.parseBoolean(params.get("running"));
            } else {
                isRunning = !isRunning;
            }
            sendJsonResponse(exchange, 200, "{\"isRunning\":" + isRunning + "}");
        }
    }

    static class ResetHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            isRunning = false;
            grid.resetMetrics();
            sendJsonResponse(exchange, 200, "{\"success\":true}");
        }
    }

    static class PresetHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> params = parseQueryParams(
                exchange.getRequestURI()
                    .getQuery());
            String name = params.getOrDefault("name", "BREEDER_7X7");
            isRunning = false;
            grid.loadPreset(name);
            sendJsonResponse(exchange, 200, "{\"success\":true,\"preset\":\"" + name + "\"}");
        }
    }

    static class SetTileHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> params = parseQueryParams(
                exchange.getRequestURI()
                    .getQuery());
            try {
                int x = Integer.parseInt(params.get("x"));
                int y = Integer.parseInt(params.get("y"));
                String typeStr = params.get("type");
                SimTile.TileType type = SimTile.TileType.fromCode(typeStr);
                grid.setTile(x, y, type);
                sendJsonResponse(exchange, 200, "{\"success\":true}");
            } catch (Exception e) {
                sendJsonResponse(exchange, 400, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    static class SetTierHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> params = parseQueryParams(
                exchange.getRequestURI()
                    .getQuery());
            try {
                int tier = Integer.parseInt(params.get("tier"));
                grid.setPipeTier(tier);
                sendJsonResponse(exchange, 200, "{\"success\":true,\"tier\":" + tier + "}");
            } catch (Exception e) {
                sendJsonResponse(exchange, 400, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    static class SetTurbineHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> params = parseQueryParams(
                exchange.getRequestURI()
                    .getQuery());
            try {
                if (params.containsKey("material")) {
                    grid.setTurbineMaterial(TurbineCalculator.TurbineMaterial.fromString(params.get("material")));
                }
                if (params.containsKey("size")) {
                    grid.setTurbineSize(TurbineCalculator.TurbineSize.fromString(params.get("size")));
                }
                if (params.containsKey("fitting")) {
                    grid.setTurbineFitting(TurbineCalculator.FittingMode.fromString(params.get("fitting")));
                }
                sendJsonResponse(exchange, 200, "{\"success\":true}");
            } catch (Exception e) {
                sendJsonResponse(exchange, 400, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    static class SetParamsHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> params = parseQueryParams(
                exchange.getRequestURI()
                    .getQuery());
            try {
                if ("true".equalsIgnoreCase(params.get("reset"))) {
                    NuclearSimulationEngine.resetDefaultParameters();
                    if (grid != null) {
                        grid.updateHatchCapacities(NuclearSimulationEngine.hatchCoolantCapacity);
                    }
                    sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Defaults restored\"}");
                    return;
                }

                if (params.containsKey("hatchCapacity")) {
                    int cap = Integer.parseInt(params.get("hatchCapacity"));
                    NuclearSimulationEngine.hatchCoolantCapacity = Math.max(100, cap);
                    if (grid != null) {
                        grid.updateHatchCapacities(NuclearSimulationEngine.hatchCoolantCapacity);
                    }
                }
                if (params.containsKey("turnoverCurve")) {
                    NuclearSimulationEngine.turnoverCurve = NuclearSimulationEngine.TurnoverCurve
                        .fromString(params.get("turnoverCurve"));
                }
                if (params.containsKey("turnoverDeltaTMax")) {
                    NuclearSimulationEngine.turnoverDeltaTMax = Double.parseDouble(params.get("turnoverDeltaTMax"));
                }
                if (params.containsKey("turnoverExponent")) {
                    NuclearSimulationEngine.turnoverExponent = Double.parseDouble(params.get("turnoverExponent"));
                }
                if (params.containsKey("coolantFeedRate")) {
                    NuclearSimulationEngine.coolantFeedRate = Integer.parseInt(params.get("coolantFeedRate"));
                }
                if (params.containsKey("coolingHeat")) {
                    NuclearSimulationEngine.coolingHeatPerLiter = Double.parseDouble(params.get("coolingHeat"));
                }
                if (params.containsKey("tempLow")) {
                    NuclearSimulationEngine.tempThresholdLow = Double.parseDouble(params.get("tempLow"));
                }
                if (params.containsKey("tempHigh")) {
                    NuclearSimulationEngine.tempThresholdHigh = Double.parseDouble(params.get("tempHigh"));
                }
                if (params.containsKey("reactivityPow")) {
                    NuclearSimulationEngine.reactivityPower = Double.parseDouble(params.get("reactivityPow"));
                }
                if (params.containsKey("fissionMult")) {
                    NuclearSimulationEngine.thermalFissionMultiplier = Double.parseDouble(params.get("fissionMult"));
                }
                if (params.containsKey("fissionHeat")) {
                    NuclearSimulationEngine.fissionHeatPerNeutron = Double.parseDouble(params.get("fissionHeat"));
                }
                if (params.containsKey("hpBoil")) {
                    NuclearSimulationEngine.hpWaterBoilingPoint = Double.parseDouble(params.get("hpBoil"));
                }
                if (params.containsKey("ambientTemp")) {
                    NuclearSimulationEngine.setAmbientTemperature(Double.parseDouble(params.get("ambientTemp")));
                }
                if (params.containsKey("ic2CoolantHeat")) {
                    NuclearSimulationEngine.ic2CoolantHeatPerLiter = Double.parseDouble(params.get("ic2CoolantHeat"));
                }

                sendJsonResponse(exchange, 200, "{\"success\":true}");
            } catch (Exception e) {
                sendJsonResponse(exchange, 400, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    static class IndexHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String html = getIndexHtml();
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders()
                .set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private static String getIndexHtml() {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>GTNH Nuclear Reactor Simulator</title>
            <style>
              :root {
                --bg-dark: #0f141c;
                --panel-bg: #18202c;
                --border-color: #2b394e;
                --accent: #00d2ff;
                --accent-glow: rgba(0, 210, 255, 0.3);
                --danger: #ff4757;
                --warning: #ffa502;
                --success: #2ed573;
                --text-primary: #e1e7f0;
                --text-muted: #8899aa;
              }
              * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, monospace; }
              body { background: var(--bg-dark); color: var(--text-primary); min-height: 100vh; display: flex; flex-direction: column; }
              header { background: #131a24; border-bottom: 1px solid var(--border-color); padding: 12px 24px; display: flex; align-items: center; justify-content: space-between; }
              header h1 { font-size: 1.3rem; font-weight: 700; color: #fff; letter-spacing: 0.5px; display: flex; align-items: center; gap: 10px; }
              .badge { background: #1e293b; border: 1px solid var(--border-color); color: var(--accent); padding: 4px 10px; border-radius: 4px; font-size: 0.8rem; }
              .container { display: flex; flex: 1; padding: 16px; gap: 16px; }
              .panel { background: var(--panel-bg); border: 1px solid var(--border-color); border-radius: 8px; padding: 16px; display: flex; flex-direction: column; }
              .grid-panel { flex: 2; align-items: center; }
              .side-panel { flex: 1; min-width: 320px; gap: 16px; }
              .telemetry-panel { flex: 1; min-width: 320px; gap: 12px; }

              /* Touch & Interaction */
              button, select, input, .cell, .palette-btn {
                touch-action: manipulation;
                -webkit-tap-highlight-color: transparent;
              }

              /* Toolbar */
              .toolbar { display: flex; gap: 8px; margin-bottom: 16px; flex-wrap: wrap; width: 100%; align-items: center; }
              button { background: #233044; border: 1px solid var(--border-color); color: #fff; padding: 8px 14px; border-radius: 6px; cursor: pointer; font-weight: 600; font-size: 0.85rem; min-height: 38px; transition: all 0.15s; }
              button:hover { background: #2d3e58; border-color: var(--accent); }
              button.primary { background: #0077b6; border-color: #0096c7; }
              button.primary:hover { background: #0096c7; }
              button.danger { background: #b91c1c; border-color: #dc2626; }
              select { background: #233044; border: 1px solid var(--border-color); color: #fff; padding: 8px; border-radius: 6px; font-size: 0.85rem; min-height: 38px; cursor: pointer; }

              /* Core Grid */
              #reactor-grid { display: grid; gap: 6px; background: #0a0d13; padding: 12px; border-radius: 8px; border: 2px solid var(--border-color); margin-top: 10px; user-select: none; }
              .cell { width: 56px; height: 56px; border-radius: 6px; display: flex; flex-direction: column; align-items: center; justify-content: center; font-size: 0.7rem; font-weight: bold; cursor: pointer; border: 1px solid rgba(255,255,255,0.1); position: relative; transition: transform 0.1s; user-select: none; }
              @media (hover: hover) {
                .cell:hover { transform: scale(1.06); z-index: 10; border-color: #fff; }
              }
              .cell.selected { border: 2px solid var(--accent); box-shadow: 0 0 10px var(--accent-glow); }
              .cell .cell-temp { font-size: 0.65rem; opacity: 0.9; }
              .cell .cell-code { font-size: 0.8rem; }

              /* Palette */
              .palette { display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px; }
              .palette-btn { padding: 8px; text-align: left; font-size: 0.75rem; border-radius: 4px; display: flex; align-items: center; gap: 8px; min-height: 38px; }
              .palette-btn.active { border-color: var(--accent); background: #29384f; box-shadow: inset 0 0 5px var(--accent-glow); }

              /* Stats display */
              .stat-row { display: flex; justify-content: space-between; padding: 6px 0; border-bottom: 1px solid rgba(255,255,255,0.05); font-size: 0.85rem; }
              .stat-val { font-weight: 700; color: #fff; }
              .stat-val.safe { color: var(--success); }
              .stat-val.warn { color: var(--warning); }
              .stat-val.danger { color: var(--danger); }

              /* Alert banner */
              #alert-box { display: none; background: rgba(255, 71, 87, 0.2); border: 1px solid var(--danger); color: #ffb8b8; padding: 12px; border-radius: 6px; margin-bottom: 12px; width: 100%; font-size: 0.85rem; }

              /* Canvas Chart */
              canvas#chartCanvas { width: 100%; height: 160px; background: #0a0d13; border-radius: 6px; border: 1px solid var(--border-color); }

              /* Mobile & Responsive Layout */
              @media (max-width: 1024px) {
                header {
                  padding: 12px 16px;
                  flex-direction: column;
                  align-items: flex-start;
                  gap: 10px;
                }
                .container {
                  flex-direction: column;
                  padding: 10px;
                  gap: 14px;
                }
                .grid-panel, .side-panel, .telemetry-panel {
                  min-width: 0;
                  width: 100%;
                }
                .grid-panel { order: 1; }
                .side-panel { order: 2; }
                .telemetry-panel { order: 3; }
                .toolbar {
                  gap: 6px;
                }
                .toolbar button, .toolbar select {
                  flex: 1 1 calc(50% - 6px);
                  font-size: 0.8rem;
                  padding: 8px 10px;
                }
                #reactor-grid {
                  max-width: 100%;
                  overflow-x: auto;
                  -webkit-overflow-scrolling: touch;
                  margin: 10px auto;
                  justify-content: center;
                }
              }
            </style>
            </head>
            <body>
            <header>
              <h1><span>⚛</span> GTNH Nuclear Reactor Standalone Simulator</h1>
              <div style="display:flex; gap:10px; align-items:center;">
                <span class="badge" id="casing-badge">Electrum Casing (1000°C Max)</span>
                <span class="badge" id="status-badge" style="color:var(--success);">STATUS: STANDBY</span>
              </div>
            </header>

            <div class="container">
              <!-- Reactor Grid View -->
              <div class="panel grid-panel">
                <div id="alert-box"></div>
                <div class="toolbar">
                  <button class="primary" id="btn-play" onclick="togglePlay()">▶ Start Simulation</button>
                  <button onclick="stepSim(1)">Step +1</button>
                  <button onclick="stepSim(10)">Step +10</button>
                  <button onclick="stepSim(100)">Step +100</button>
                  <button class="danger" onclick="resetSim()">↺ Reset</button>
                  <select id="preset-select" onchange="loadPreset(this.value)">
                    <option value="BEST_ELECTRUM_5X5">⭐ EV 60A: Electrum 5x5 (123k EU/t · 17.9m)</option>
                    <option value="BEST_PLATINUM_7X7" selected>⭐ IV 60A: Platinum 7x7 Breeder (491k EU/t · 15.8m)</option>
                    <option value="BEST_OSMIUM_7X7">⭐ LuV 60A: Osmium 7x7 Superheated (1.97M EU/t · 28.1m)</option>
                    <option value="BEST_QUANTIUM_9X9">⭐ ZPM 60A: Quantium 9x9 CANDU (7.86M EU/t · 41.0m)</option>
                    <option value="BEST_FLUXED_9X9">⭐ UV 60A: Fluxed 9x9 Supercritical (31.5M EU/t · 17.1m)</option>
                    <option value="BEST_PLUTONIUM_9X9">⭐ UHV High-Power: Black Plutonium 9x9 (6.98M EU/t · 37.3m)</option>
                  </select>
                  <select id="tier-select" onchange="changeTier(this.value)">
                    <option value="0">Electrum (1000°C)</option>
                    <option value="1" selected>Platinum (1400°C)</option>
                    <option value="2">Osmium (1800°C)</option>
                    <option value="3">Quantium (2200°C)</option>
                    <option value="4">Fluxed Electrum (2600°C)</option>
                    <option value="5">Black Plutonium (3200°C)</option>
                  </select>
                </div>

                <div id="reactor-grid"></div>
              </div>

              <!-- Palette & Inspector -->
              <div class="panel side-panel">
                <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:8px;">
                  <h3>Interaction Mode</h3>
                  <span class="badge" id="mode-badge" style="color:var(--accent);">INSPECT ONLY</span>
                </div>
                <div style="display:flex; gap:8px; margin-bottom:12px;">
                  <button id="btn-mode-inspect" class="primary" onclick="setInteractionMode('INSPECT')" style="flex:1;" title="Click cells to view live details without modifying (Shortcut: 'i' or Esc)">🔍 Inspect Only</button>
                  <button id="btn-mode-paint" onclick="setInteractionMode('PAINT')" style="flex:1;" title="Click cells to place selected component (Shortcut: 'p')">🖌️ Paint / Place</button>
                </div>

                <h3>Tile Palette</h3>
                <p id="palette-hint" style="font-size:0.75rem; color:var(--text-muted); margin-bottom:8px;">Select a component below to enter Paint Mode, or use Inspect Mode to safely check hatches.</p>
                <div class="palette" id="palette-container"></div>

                <hr style="border:0; border-top:1px solid var(--border-color); margin:12px 0;">

                <h3>Selected Tile Inspector</h3>
                <div id="tile-inspector" style="font-size:0.85rem; color:var(--text-muted);">
                  Click any grid cell to inspect its live telemetry and coolant levels.
                </div>

                <hr style="border:0; border-top:1px solid var(--border-color); margin:12px 0;">

                <div style="background:#131a24; border:1px solid var(--border-color); border-radius:6px; padding:12px;">
                  <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:8px;">
                    <h3 style="font-size:0.95rem; color:var(--warning);">⚙️ Simulation Tuning</h3>
                    <button onclick="resetSimParams()" style="padding:2px 8px; font-size:0.75rem;" title="Reset parameters to defaults">↺ Reset</button>
                  </div>
                  <div style="margin-bottom:8px;">
                    <label style="font-size:0.75rem; color:var(--text-muted); display:block; margin-bottom:2px;">Tuning Profile:</label>
                    <select id="sim-profile-select" onchange="applyProfilePreset(this.value)" style="width:100%;">
                      <option value="BALANCED" selected>Balanced / Default</option>
                      <option value="DANGER">Meltdown Hazard (High Danger)</option>
                      <option value="FLASH_BOIL">Flash Boiling (Low Capacity / Fast Turnover)</option>
                      <option value="SAFE">Conservative / Low Heat</option>
                    </select>
                  </div>
                  <div style="display:grid; grid-template-columns: 1fr 1fr; gap:6px; margin-bottom:6px;">
                    <div>
                      <label style="font-size:0.7rem; color:var(--text-muted);">Hatch Capacity:</label>
                      <select id="p-hatch-cap" onchange="submitSimParams()" style="width:100%;">
                        <option value="500">500 L</option>
                        <option value="1000">1,000 L</option>
                        <option value="2000" selected>2,000 L (Default)</option>
                        <option value="4000">4,000 L</option>
                        <option value="8000">8,000 L</option>
                        <option value="16000">16,000 L (GT)</option>
                      </select>
                    </div>
                    <div>
                      <label style="font-size:0.7rem; color:var(--text-muted);">Feed Rate:</label>
                      <select id="p-feed-rate" onchange="submitSimParams()" style="width:100%;">
                        <option value="500">500 L/t</option>
                        <option value="1000">1,000 L/t</option>
                        <option value="2000" selected>2,000 L/t</option>
                        <option value="4000">4,000 L/t</option>
                        <option value="999999">Instant Max</option>
                      </select>
                    </div>
                  </div>
                  <div style="display:grid; grid-template-columns: 1fr 1fr; gap:6px; margin-bottom:6px;">
                    <div>
                      <label style="font-size:0.7rem; color:var(--text-muted);">Turnover Curve:</label>
                      <select id="p-turn-curve" onchange="submitSimParams()" style="width:100%;">
                        <option value="EXPONENTIAL" selected>Exponential</option>
                        <option value="LINEAR">Linear</option>
                        <option value="SIGMOID">Sigmoid (S-Curve)</option>
                        <option value="STEP">Step-Based</option>
                      </select>
                    </div>
                    <div>
                      <label style="font-size:0.7rem; color:var(--text-muted);">Turnover &Delta;T Max:</label>
                      <input id="p-turn-dt" type="number" step="10" value="100" onchange="submitSimParams()" style="width:100%; background:#233044; border:1px solid var(--border-color); color:#fff; padding:6px; border-radius:6px; font-size:0.8rem;">
                    </div>
                  </div>
                  <div style="display:grid; grid-template-columns: 1fr 1fr; gap:6px; margin-bottom:6px;">
                    <div>
                      <label style="font-size:0.7rem; color:var(--text-muted);">Turnover Exponent:</label>
                      <input id="p-turn-exp" type="number" step="0.1" value="1.5" onchange="submitSimParams()" style="width:100%; background:#233044; border:1px solid var(--border-color); color:#fff; padding:6px; border-radius:6px; font-size:0.8rem;">
                    </div>
                    <div>
                      <label style="font-size:0.7rem; color:var(--text-muted);">HP Boil Point (°C):</label>
                      <input id="p-hp-boil" type="number" step="10" value="200" onchange="submitSimParams()" style="width:100%; background:#233044; border:1px solid var(--border-color); color:#fff; padding:6px; border-radius:6px; font-size:0.8rem;">
                    </div>
                  </div>
                  <div style="display:grid; grid-template-columns: 1fr 1fr; gap:6px; margin-bottom:6px;">
                    <div>
                      <label style="font-size:0.7rem; color:var(--text-muted);">Reactivity T-Low:</label>
                      <input id="p-t-low" type="number" step="50" value="800" onchange="submitSimParams()" style="width:100%; background:#233044; border:1px solid var(--border-color); color:#fff; padding:6px; border-radius:6px; font-size:0.8rem;">
                    </div>
                    <div>
                      <label style="font-size:0.7rem; color:var(--text-muted);">Reactivity T-High:</label>
                      <input id="p-t-high" type="number" step="50" value="2800" onchange="submitSimParams()" style="width:100%; background:#233044; border:1px solid var(--border-color); color:#fff; padding:6px; border-radius:6px; font-size:0.8rem;">
                    </div>
                  </div>
                  <div style="display:grid; grid-template-columns: 1fr 1fr; gap:6px; margin-bottom:6px;">
                    <div>
                      <label style="font-size:0.7rem; color:var(--text-muted);">Reactivity Pow (p):</label>
                      <input id="p-react-pow" type="number" step="0.1" value="1.2" onchange="submitSimParams()" style="width:100%; background:#233044; border:1px solid var(--border-color); color:#fff; padding:6px; border-radius:6px; font-size:0.8rem;">
                    </div>
                    <div>
                      <label style="font-size:0.7rem; color:var(--text-muted);">Fission Mult (k):</label>
                      <input id="p-fiss-mult" type="number" step="0.05" value="1.1" onchange="submitSimParams()" style="width:100%; background:#233044; border:1px solid var(--border-color); color:#fff; padding:6px; border-radius:6px; font-size:0.8rem;">
                    </div>
                  </div>
                  <div style="display:grid; grid-template-columns: 1fr 1fr; gap:6px; margin-top:6px;">
                    <div>
                      <label style="font-size:0.7rem; color:var(--text-muted);">Water Heat (EU/L):</label>
                      <input id="p-cooling-heat" type="number" step="0.5" value="4.0" onchange="submitSimParams()" style="width:100%; background:#233044; border:1px solid var(--border-color); color:#fff; padding:6px; border-radius:6px; font-size:0.8rem;">
                    </div>
                    <div>
                      <label style="font-size:0.7rem; color:var(--text-muted);">IC2 Heat (EU/L):</label>
                      <input id="p-ic2-cooling-heat" type="number" step="1.0" value="20.0" onchange="submitSimParams()" style="width:100%; background:#233044; border:1px solid var(--border-color); color:#fff; padding:6px; border-radius:6px; font-size:0.8rem;">
                    </div>
                  </div>
                  <div style="margin-top:6px;">
                    <label style="font-size:0.7rem; color:var(--text-muted);">Ambient Temp (°C):</label>
                    <input id="p-ambient-temp" type="number" step="1.0" value="24.0" onchange="submitSimParams()" style="width:100%; background:#233044; border:1px solid var(--border-color); color:#fff; padding:6px; border-radius:6px; font-size:0.8rem;">
                  </div>
                </div>
              </div>

              <!-- Telemetry & Live Graphs -->
              <div class="panel telemetry-panel">
                <div style="background:#131a24; border:1px solid var(--border-color); border-radius:6px; padding:12px; margin-bottom:12px;">
                  <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:8px;">
                    <h3 style="font-size:0.95rem; color:var(--accent);">⚡ Turbine Power (XLST & EHE)</h3>
                    <span class="badge" id="stat-power-tier" style="font-weight:bold; color:var(--success);">0 EU/t (OFF)</span>
                  </div>
                  <div style="display:grid; grid-template-columns: 1fr 1fr; gap:6px; margin-bottom:6px;">
                    <select id="turbine-mat" onchange="onTurbineConfigChange()" title="Turbine Rotor Material"></select>
                    <select id="turbine-size" onchange="onTurbineConfigChange()" title="Turbine Rotor Size">
                      <option value="SMALL">Small (0.33x)</option>
                      <option value="NORMAL">Normal (0.67x)</option>
                      <option value="LARGE" selected>Large (1.00x)</option>
                      <option value="HUGE">Huge (1.33x)</option>
                    </select>
                  </div>
                  <div style="margin-bottom:8px;">
                    <select id="turbine-fitting" onchange="onTurbineConfigChange()" style="width:100%;" title="Housing Fitting Mode">
                      <option value="TIGHT" selected>Fitting: Tight (Optimal)</option>
                      <option value="LOOSE">Fitting: Loose</option>
                    </select>
                  </div>
                  <div class="stat-row"><span>Rotor Efficiency:</span><span class="stat-val" id="turb-eff">100 %</span></div>
                  <div class="stat-row"><span>Opt Flow / Turbine:</span><span class="stat-val" id="turb-opt-flow">0 L/t</span></div>
                  <hr style="border:0; border-top:1px solid var(--border-color); margin:6px 0;">
                  <div class="stat-row"><span>XLST-SC (Supercritical):</span><span class="stat-val" id="turb-sc-pwr">0 EU/t</span></div>
                  <div class="stat-row"><span>XLST-HP (Superheated/HW):</span><span class="stat-val" id="turb-hp-pwr">0 EU/t</span></div>
                  <div class="stat-row"><span>XLST (Regular Steam):</span><span class="stat-val" id="turb-reg-pwr">0 EU/t</span></div>
                  <div class="stat-row" id="ehe-row" style="display:none;"><span>EHE Mode / DW:</span><span class="stat-val" id="turb-ehe-info" style="color:#38bdf8;">None</span></div>
                  <hr style="border:0; border-top:1px solid var(--border-color); margin:6px 0;">
                  <div class="stat-row" style="font-size:0.75rem;"><span>Live Flows (L/t):</span><span class="stat-val" id="turb-flows" style="font-size:0.75rem;">Reg: 0 | SH: 0 | SC: 0</span></div>
                </div>

                <h3>Reactor Telemetry</h3>
                <div class="stat-row"><span>Simulation Tick:</span><span class="stat-val" id="stat-tick">0</span></div>
                <div class="stat-row"><span>Core Peak Temp:</span><span class="stat-val" id="stat-max-temp">20.0 °C</span></div>
                <div class="stat-row"><span>Core Average Temp:</span><span class="stat-val" id="stat-avg-temp">20.0 °C</span></div>
                <div class="stat-row"><span>Reactivity Efficiency:</span><span class="stat-val" id="stat-eff">100 %</span></div>
                <div class="stat-row"><span>Neutron Output:</span><span class="stat-val" id="stat-neutrons">0 / tick</span></div>
                <div class="stat-row"><span>Thermal Absorbed:</span><span class="stat-val" id="stat-thermal">0 / tick</span></div>
                <div class="stat-row"><span>Fast Absorbed:</span><span class="stat-val" id="stat-fast">0 / tick</span></div>
                <div class="stat-row"><span>Steam Produced:</span><span class="stat-val" id="stat-steam">0 L</span></div>
                <div class="stat-row"><span>Deuterium Bred:</span><span class="stat-val" id="stat-deuterium" style="color:var(--accent);">0 L</span></div>
                <div class="stat-row"><span>Tritium Bred:</span><span class="stat-val" id="stat-tritium" style="color:#d946ef;">0 L</span></div>
                <div class="stat-row"><span>Power Generation:</span><span class="stat-val safe" id="stat-power">0 EU/t</span></div>

                <h3 style="margin-top:12px;">Temperature Trend</h3>
                <canvas id="chartCanvas" width="300" height="150"></canvas>
              </div>
            </div>

            <script>
            let currentState = null;
            let selectedTilePos = null;
            let activePaletteType = "FUEL_URANIUM_QUAD";
            let interactionMode = "INSPECT";

            const PALETTE = [
              { type: "EMPTY", name: "Empty Slot", code: ".", color: "#1a2230" },
              { type: "FUEL_URANIUM_QUAD", name: "Uranium Quad Rod", code: "U4", color: "#22c55e" },
              { type: "FUEL_MOX_QUAD", name: "MOX Quad Rod", code: "M4", color: "#eab308" },
              { type: "FUEL_THORIUM_QUAD", name: "Thorium Quad Rod", code: "T4", color: "#a855f7" },
              { type: "FUEL_NAQUADAH", name: "Naquadah Rod", code: "NQ", color: "#ec4899" },
              { type: "HATCH_DISTILLED_WATER", name: "Distilled Water Hatch", code: "HD", color: "#38bdf8" },
              { type: "HATCH_HP_DISTILLED_WATER", name: "HP Distilled Water Hatch", code: "HP", color: "#0284c7" },
              { type: "HATCH_HEAVY_WATER", name: "Heavy Water Hatch", code: "HW", color: "#6366f1" },
              { type: "HATCH_HP_HEAVY_WATER", name: "HP Heavy Water Hatch", code: "HH", color: "#4338ca" },
              { type: "HATCH_IC2_COOLANT", name: "IC2 Coolant Hatch", code: "HC", color: "#14b8a6" },
              { type: "REFLECTOR_BERYLLIUM", name: "Beryllium Reflector", code: "RB", color: "#94a3b8" },
              { type: "REFLECTOR_CARBON", name: "Carbon Reflector", code: "RC", color: "#475569" },
              { type: "CONTROL_ROD", name: "Boron Control Rod", code: "CR", color: "#b91c1c" },
              { type: "COOLANT_CELL_60K", name: "60k Coolant Cell", code: "C6", color: "#06b6d4" },
              { type: "BETAVOLTAIC_HV", name: "Betavoltaic Cell (HV)", code: "BH", color: "#f59e0b" },
              { type: "BETAVOLTAIC_EV", name: "Betavoltaic Cell (EV)", code: "BV", color: "#f97316" }
            ];

            const TURBINE_MATERIALS = [
              "HSS-E", "HSS-S", "Elven Elementium", "Oriharukon", "Shadow Metal",
              "Ichorium", "Duranium", "Gaia Spirit", "Adamantium",
              "Ext. Unst. Naquadah", "Cosmic Neutronium", "Infinity",
              "Tungstensteel", "Titanium"
            ];

            function initTurbineSelects() {
              const matSelect = document.getElementById("turbine-mat");
              matSelect.innerHTML = "";
              TURBINE_MATERIALS.forEach(m => {
                const opt = document.createElement("option");
                opt.value = m;
                opt.innerText = m;
                if (m === "HSS-E") opt.selected = true;
                matSelect.appendChild(opt);
              });
            }

            async function onTurbineConfigChange() {
              const mat = document.getElementById("turbine-mat").value;
              const size = document.getElementById("turbine-size").value;
              const fitting = document.getElementById("turbine-fitting").value;
              await fetch(`/api/set-turbine?material=${encodeURIComponent(mat)}&size=${encodeURIComponent(size)}&fitting=${encodeURIComponent(fitting)}`);
              await fetchState();
              schedulePoll();
            }

            function getVoltageTier(eut) {
              if (eut <= 0) return "OFF";
              if (eut <= 32) return "LV";
              if (eut <= 128) return "MV";
              if (eut <= 512) return "HV";
              if (eut <= 2048) return "EV";
              if (eut <= 8192) return "IV";
              if (eut <= 32768) return "LuV";
              if (eut <= 131072) return "ZPM";
              if (eut <= 524288) return "UV";
              if (eut <= 2097152) return "UHV";
              if (eut <= 8388608) return "UEV";
              if (eut <= 33554432) return "UIV";
              if (eut <= 134217728) return "UMV";
              return "UXV+";
            }

            function setInteractionMode(mode) {
              interactionMode = mode;
              const btnInspect = document.getElementById("btn-mode-inspect");
              const btnPaint = document.getElementById("btn-mode-paint");
              const badge = document.getElementById("mode-badge");

              if (mode === "INSPECT") {
                btnInspect.className = "primary";
                btnPaint.className = "";
                badge.innerText = "INSPECT ONLY";
                badge.style.color = "var(--accent)";
                document.querySelectorAll(".palette-btn").forEach(b => b.classList.remove("active"));
              } else {
                btnInspect.className = "";
                btnPaint.className = "primary";
                badge.innerText = "PAINT MODE";
                badge.style.color = "var(--warning)";
                document.querySelectorAll(".palette-btn").forEach(b => {
                  if (b.dataset.type === activePaletteType) {
                    b.classList.add("active");
                  } else {
                    b.classList.remove("active");
                  }
                });
              }
            }

            function initPalette() {
              const container = document.getElementById("palette-container");
              container.innerHTML = "";
              PALETTE.forEach(p => {
                const btn = document.createElement("button");
                btn.className = "palette-btn" + (interactionMode === "PAINT" && p.type === activePaletteType ? " active" : "");
                btn.dataset.type = p.type;
                btn.style.borderLeft = "4px solid " + p.color;
                btn.innerHTML = `<strong>${p.code}</strong> <span>${p.name}</span>`;
                btn.onclick = () => {
                  activePaletteType = p.type;
                  setInteractionMode("PAINT");
                };
                container.appendChild(btn);
              });
            }

            function getTempColor(t) {
              if (t < 50) return "#1e293b";
              if (t < 150) return "#1d4ed8";
              if (t < 350) return "#059669";
              if (t < 700) return "#d97706";
              if (t < 1200) return "#dc2626";
              if (t < 1800) return "#9333ea";
              return "#f43f5e";
            }

            let isFetching = false;
            let pollTimer = null;

            async function fetchState() {
              if (isFetching) return;
              isFetching = true;
              try {
                const res = await fetch("/api/state");
                if (res.ok) {
                  currentState = await res.json();
                  renderUI();
                }
              } catch (e) {
                console.error("Fetch error", e);
              } finally {
                isFetching = false;
              }
            }

            function schedulePoll() {
              clearTimeout(pollTimer);
              const delay = (currentState && currentState.isRunning) ? 250 : 1000;
              pollTimer = setTimeout(async () => {
                await fetchState();
                schedulePoll();
              }, delay);
            }

            function renderUI() {
              if (!currentState) return;

              // Header badges
              document.getElementById("casing-badge").innerText = currentState.pipeTierName + " (Max " + currentState.maxSafeTemp + "°C)";
              const statusBadge = document.getElementById("status-badge");
              const playBtn = document.getElementById("btn-play");
              const alertBox = document.getElementById("alert-box");

              if (currentState.exploded) {
                statusBadge.innerText = "MELTDOWN / EXPLODED";
                statusBadge.style.color = "var(--danger)";
                playBtn.innerText = "▶ Start Simulation";
                playBtn.disabled = true;
                alertBox.style.display = "block";
                alertBox.innerHTML = `<strong>⚠️ CRITICAL FAILURE:</strong> ${currentState.explosionReason}`;
              } else {
                alertBox.style.display = "none";
                playBtn.disabled = false;
                if (currentState.isRunning) {
                  statusBadge.innerText = "SIMULATION RUNNING";
                  statusBadge.style.color = "var(--accent)";
                  playBtn.innerText = "⏸ Pause Simulation";
                } else {
                  statusBadge.innerText = "SIMULATION PAUSED";
                  statusBadge.style.color = "var(--success)";
                  playBtn.innerText = "▶ Resume Simulation";
                }
              }

              // Telemetry
              document.getElementById("stat-tick").innerText = currentState.currentTick;
              document.getElementById("stat-max-temp").innerText = currentState.coreMaxTemp + " °C";
              document.getElementById("stat-avg-temp").innerText = currentState.coreAvgTemp + " °C";
              document.getElementById("stat-eff").innerText = (currentState.efficiency * 100).toFixed(1) + " %";
              document.getElementById("stat-neutrons").innerText = currentState.lastNeutrons + " / tick";
              document.getElementById("stat-thermal").innerText = currentState.thermalAbsorbed + " / tick";
              document.getElementById("stat-fast").innerText = currentState.fastAbsorbed + " / tick";
              document.getElementById("stat-steam").innerText = currentState.totalSteam.toLocaleString() + " L";
              document.getElementById("stat-deuterium").innerText = currentState.totalDeuterium.toLocaleString() + " L";
              document.getElementById("stat-tritium").innerText = currentState.totalTritium.toLocaleString() + " L";

              // Turbine config sync
              const activeEl = document.activeElement;
              const turbMat = document.getElementById("turbine-mat");
              if (activeEl !== turbMat && currentState.turbineMaterial && turbMat.value !== currentState.turbineMaterial) {
                turbMat.value = currentState.turbineMaterial;
              }
              const turbSize = document.getElementById("turbine-size");
              if (activeEl !== turbSize && currentState.turbineSize && turbSize.value !== currentState.turbineSize) {
                turbSize.value = currentState.turbineSize;
              }
              const turbFitting = document.getElementById("turbine-fitting");
              if (activeEl !== turbFitting && currentState.turbineFitting && turbFitting.value !== currentState.turbineFitting) {
                turbFitting.value = currentState.turbineFitting;
              }
              const tierSelect = document.getElementById("tier-select");
              if (activeEl !== tierSelect && currentState.pipeTier !== undefined && parseInt(tierSelect.value) !== currentState.pipeTier) {
                tierSelect.value = currentState.pipeTier;
              }

              // Turbine telemetry
              if (currentState.powerEstimate) {
                const pe = currentState.powerEstimate;
                const tier = getVoltageTier(pe.totalPowerEUt);
                document.getElementById("stat-power-tier").innerText = Math.round(pe.totalPowerEUt).toLocaleString() + " EU/t (" + tier + ")";
                document.getElementById("stat-power").innerText = Math.round(pe.totalPowerEUt).toLocaleString() + " EU/t (" + tier + ")";
                document.getElementById("turb-eff").innerText = (pe.efficiency * 100).toFixed(1) + " %";
                document.getElementById("turb-opt-flow").innerText = Math.round(pe.optFlowPerTurbine).toLocaleString() + " L/t";
                document.getElementById("turb-sc-pwr").innerText = Math.round(pe.xlstScPowerEUt).toLocaleString() + " EU/t (" + pe.xlstScTurbinesNeeded.toFixed(2) + " units)";
                document.getElementById("turb-hp-pwr").innerText = Math.round(pe.xlstHpPowerEUt).toLocaleString() + " EU/t (" + pe.xlstHpTurbinesNeeded.toFixed(2) + " units)";
                document.getElementById("turb-reg-pwr").innerText = Math.round(pe.xlstPowerEUt).toLocaleString() + " EU/t (" + pe.xlstTurbinesNeeded.toFixed(2) + " units)";

                const eheRow = document.getElementById("ehe-row");
                if (pe.eheMode && pe.eheMode !== "NONE") {
                  eheRow.style.display = "flex";
                  document.getElementById("turb-ehe-info").innerText = pe.eheMode + " (" + Math.round(pe.eheSteamProduced).toLocaleString() + " L/t stm, " + Math.round(pe.eheDistilledWaterConsumed).toLocaleString() + " L/t DW)";
                } else {
                  eheRow.style.display = "none";
                }

                document.getElementById("turb-flows").innerText =
                  `Reg: ${Math.round(currentState.flowRegularSteam || 0)} | SH: ${Math.round(currentState.flowSuperheatedSteam || 0)} | SC: ${Math.round(currentState.flowSupercriticalSteam || 0)} | HW: ${Math.round(currentState.flowHeavyWaterSteam || 0)}`;
              } else {
                const power = (currentState.history && currentState.history.length > 0)
                  ? currentState.history[currentState.history.length - 1].power
                  : 0;
                document.getElementById("stat-power").innerText = power.toLocaleString() + " EU/t";
              }

              // Reconcile and update grid DOM without destroying elements
              updateGridDOM();

              if (selectedTilePos) {
                const tile = currentState.tiles.find(t => t.x === selectedTilePos.x && t.y === selectedTilePos.y);
                if (tile) renderInspector(tile);
              }

              if (currentState.params) {
                syncParamsToUI(currentState.params);
              }

              renderChart();
            }

            function updateGridDOM() {
              const gridElem = document.getElementById("reactor-grid");
              const width = currentState.width;
              const height = currentState.height;
              const totalCells = width * height;

              if (gridElem.children.length !== totalCells) {
                gridElem.innerHTML = "";
                gridElem.style.gridTemplateColumns = `repeat(${width}, 56px)`;
                for (let y = 0; y < height; y++) {
                  for (let x = 0; x < width; x++) {
                    const cell = document.createElement("div");
                    cell.className = "cell";
                    cell.dataset.x = x;
                    cell.dataset.y = y;
                    cell.innerHTML = `<span class="cell-code"></span><span class="cell-temp"></span>`;
                    cell.addEventListener("click", (e) => {
                      const cx = parseInt(cell.dataset.x);
                      const cy = parseInt(cell.dataset.y);
                      handleCellClick(cx, cy, e);
                    });
                    gridElem.appendChild(cell);
                  }
                }
              }

              currentState.tiles.forEach((t, i) => {
                const cell = gridElem.children[i];
                if (!cell) return;
                cell.dataset.x = t.x;
                cell.dataset.y = t.y;

                const isSelected = selectedTilePos && selectedTilePos.x === t.x && selectedTilePos.y === t.y;
                const desiredClass = "cell" + (isSelected ? " selected" : "");
                if (cell.className !== desiredClass) {
                  cell.className = desiredClass;
                }

                const bg = getTempColor(t.temp);
                if (cell.style.backgroundColor !== bg) {
                  cell.style.backgroundColor = bg;
                }

                const codeEl = cell.children[0];
                const tempEl = cell.children[1];
                if (codeEl.textContent !== t.code) {
                  codeEl.textContent = t.code;
                }
                const tempStr = Math.round(t.temp) + "°C";
                if (tempEl.textContent !== tempStr) {
                  tempEl.textContent = tempStr;
                }
              });
            }

            function handleCellClick(x, y, e) {
              const tile = currentState && currentState.tiles.find(t => t.x === x && t.y === y);
              if (!tile) return;
              if (interactionMode === "INSPECT" || (e && e.shiftKey)) {
                selectCell(tile);
              } else {
                setTile(x, y, activePaletteType);
                selectCell(tile);
              }
            }

            function selectCell(tile) {
              selectedTilePos = { x: tile.x, y: tile.y };
              renderInspector(tile);
              const gridElem = document.getElementById("reactor-grid");
              for (let i = 0; i < gridElem.children.length; i++) {
                const c = gridElem.children[i];
                const isSel = parseInt(c.dataset.x) === tile.x && parseInt(c.dataset.y) === tile.y;
                const desired = "cell" + (isSel ? " selected" : "");
                if (c.className !== desired) c.className = desired;
              }
            }

            function renderInspector(t) {
              const el = document.getElementById("tile-inspector");
              let extraHtml = "";
              if (t.isFuel) {
                extraHtml = `
                  <div class="stat-row"><span>Durability:</span><span class="stat-val">${t.durability} (${t.durabilityPct}%)</span></div>
                `;
              } else if (t.fluidName && t.fluidName.length > 0) {
                const cap = t.fluidCapacity || 2000;
                const pct = Math.round((t.fluidAmount / cap) * 100);
                const dryBadge = t.wasDry
                  ? '<span class="badge" style="color:var(--danger); background:rgba(255,71,87,0.2);">⚠️ BOILED DRY</span>'
                  : '<span class="badge" style="color:var(--success);">NORMAL</span>';
                const steamFlow = t.lastProduced || 0;
                const coolantTurnover = steamFlow > 0 ? (steamFlow / 160).toFixed(0) : 0;
                extraHtml = `
                  <div class="stat-row"><span>Coolant:</span><span class="stat-val">${t.fluidName}</span></div>
                  <div class="stat-row"><span>Coolant Volume:</span><span class="stat-val">${t.fluidAmount} / ${cap} L (${pct}%)</span></div>
                  <div class="stat-row"><span>Live Turnover:</span><span class="stat-val" style="color:#38bdf8;">${coolantTurnover} L/t coolant &rarr; ${steamFlow} L/t steam</span></div>
                  <div class="stat-row"><span>Cumulative Steam:</span><span class="stat-val">${t.steamAmount ? t.steamAmount.toLocaleString() : 0} L</span></div>
                  <div class="stat-row"><span>Hatch Status:</span><span class="stat-val">${dryBadge}</span></div>
                `;
              }
              el.innerHTML = `
                <div class="stat-row"><span>Position:</span><span class="stat-val">(${t.x}, ${t.y})</span></div>
                <div class="stat-row"><span>Component:</span><span class="stat-val">${t.name}</span></div>
                <div class="stat-row"><span>Temperature:</span><span class="stat-val">${t.temp} °C</span></div>
                ${extraHtml}
              `;
            }

            function syncParamsToUI(p) {
              if (!p) return;
              const active = document.activeElement;
              if (active && active.id && (active.id.startsWith("p-") || active.id === "sim-profile-select")) return;
              const setVal = (id, val) => {
                const el = document.getElementById(id);
                if (el && active !== el && el.value != val) el.value = val;
              };
              setVal("p-hatch-cap", p.hatchCapacity);
              setVal("p-turn-curve", p.turnoverCurve);
              setVal("p-turn-dt", p.turnoverDeltaTMax);
              setVal("p-turn-exp", p.turnoverExponent);
              setVal("p-feed-rate", p.coolantFeedRate);
              setVal("p-cooling-heat", p.coolingHeatPerLiter);
              setVal("p-ic2-cooling-heat", p.ic2CoolantHeatPerLiter);
              setVal("p-ambient-temp", p.ambientTemp);
              setVal("p-t-low", p.tempThresholdLow);
              setVal("p-t-high", p.tempThresholdHigh);
              setVal("p-react-pow", p.reactivityPower);
              setVal("p-fiss-mult", p.thermalFissionMultiplier);
              setVal("p-hp-boil", p.hpWaterBoilingPoint);
            }

            async function submitSimParams() {
              const cap = document.getElementById("p-hatch-cap").value;
              const curve = document.getElementById("p-turn-curve").value;
              const dt = document.getElementById("p-turn-dt").value;
              const exp = document.getElementById("p-turn-exp").value;
              const feed = document.getElementById("p-feed-rate").value;
              const ch = document.getElementById("p-cooling-heat").value;
              const ic2Ch = document.getElementById("p-ic2-cooling-heat").value;
              const amb = document.getElementById("p-ambient-temp").value;
              const tLow = document.getElementById("p-t-low").value;
              const tHigh = document.getElementById("p-t-high").value;
              const rPow = document.getElementById("p-react-pow").value;
              const fMult = document.getElementById("p-fiss-mult").value;
              const hpBoil = document.getElementById("p-hp-boil").value;

              const url = `/api/set-params?hatchCapacity=${cap}&turnoverCurve=${curve}&turnoverDeltaTMax=${dt}&turnoverExponent=${exp}&coolantFeedRate=${feed}&coolingHeat=${ch}&ic2CoolantHeat=${ic2Ch}&ambientTemp=${amb}&tempLow=${tLow}&tempHigh=${tHigh}&reactivityPow=${rPow}&fissionMult=${fMult}&hpBoil=${hpBoil}`;
              await fetch(url);
              await fetchState();
              schedulePoll();
            }

            async function resetSimParams() {
              await fetch("/api/set-params?reset=true");
              await fetchState();
              schedulePoll();
            }

            function applyProfilePreset(profile) {
              switch (profile) {
                case "BALANCED":
                  document.getElementById("p-hatch-cap").value = 2000;
                  document.getElementById("p-turn-curve").value = "EXPONENTIAL";
                  document.getElementById("p-turn-dt").value = 100;
                  document.getElementById("p-turn-exp").value = 1.5;
                  document.getElementById("p-feed-rate").value = 2000;
                  document.getElementById("p-cooling-heat").value = 4.0;
                  document.getElementById("p-t-low").value = 800;
                  document.getElementById("p-t-high").value = 2800;
                  document.getElementById("p-react-pow").value = 1.2;
                  document.getElementById("p-fiss-mult").value = 1.1;
                  document.getElementById("p-hp-boil").value = 200;
                  break;
                case "DANGER":
                  document.getElementById("p-hatch-cap").value = 1000;
                  document.getElementById("p-turn-curve").value = "EXPONENTIAL";
                  document.getElementById("p-turn-dt").value = 80;
                  document.getElementById("p-turn-exp").value = 1.8;
                  document.getElementById("p-feed-rate").value = 1000;
                  document.getElementById("p-cooling-heat").value = 3.0;
                  document.getElementById("p-t-low").value = 950;
                  document.getElementById("p-t-high").value = 3200;
                  document.getElementById("p-react-pow").value = 1.6;
                  document.getElementById("p-fiss-mult").value = 1.25;
                  document.getElementById("p-hp-boil").value = 200;
                  break;
                case "FLASH_BOIL":
                  document.getElementById("p-hatch-cap").value = 500;
                  document.getElementById("p-turn-curve").value = "SIGMOID";
                  document.getElementById("p-turn-dt").value = 60;
                  document.getElementById("p-turn-exp").value = 2.0;
                  document.getElementById("p-feed-rate").value = 500;
                  document.getElementById("p-cooling-heat").value = 4.0;
                  document.getElementById("p-t-low").value = 800;
                  document.getElementById("p-t-high").value = 2800;
                  document.getElementById("p-react-pow").value = 1.2;
                  document.getElementById("p-fiss-mult").value = 1.1;
                  document.getElementById("p-hp-boil").value = 200;
                  break;
                case "SAFE":
                  document.getElementById("p-hatch-cap").value = 8000;
                  document.getElementById("p-turn-curve").value = "LINEAR";
                  document.getElementById("p-turn-dt").value = 150;
                  document.getElementById("p-turn-exp").value = 1.0;
                  document.getElementById("p-feed-rate").value = 8000;
                  document.getElementById("p-cooling-heat").value = 6.0;
                  document.getElementById("p-t-low").value = 650;
                  document.getElementById("p-t-high").value = 2200;
                  document.getElementById("p-react-pow").value = 1.0;
                  document.getElementById("p-fiss-mult").value = 0.95;
                  document.getElementById("p-hp-boil").value = 200;
                  break;
              }
              submitSimParams();
            }

            let lastChartTick = -1;
            function renderChart() {
              const canvas = document.getElementById("chartCanvas");
              if (!canvas) return;
              if (!currentState || !currentState.history || currentState.history.length === 0) return;
              if (currentState.currentTick === lastChartTick) return;
              lastChartTick = currentState.currentTick;

              const ctx = canvas.getContext("2d");
              const w = canvas.width;
              const h = canvas.height;
              ctx.clearRect(0, 0, w, h);

              const hist = currentState.history;
              const maxT = Math.max(100, ...hist.map(p => p.maxT));

              ctx.strokeStyle = "rgba(255,255,255,0.1)";
              ctx.beginPath();
              ctx.moveTo(0, h * 0.25); ctx.lineTo(w, h * 0.25);
              ctx.moveTo(0, h * 0.50); ctx.lineTo(w, h * 0.50);
              ctx.moveTo(0, h * 0.75); ctx.lineTo(w, h * 0.75);
              ctx.stroke();

              // Peak Temp curve (red)
              ctx.strokeStyle = "#ff4757";
              ctx.lineWidth = 2;
              ctx.beginPath();
              hist.forEach((p, idx) => {
                const x = (idx / (hist.length - 1)) * w;
                const y = h - (p.maxT / maxT) * (h - 20) - 10;
                if (idx === 0) ctx.moveTo(x, y);
                else ctx.lineTo(x, y);
              });
              ctx.stroke();

              // Average Temp curve (cyan)
              ctx.strokeStyle = "#00d2ff";
              ctx.lineWidth = 1.5;
              ctx.beginPath();
              hist.forEach((p, idx) => {
                const x = (idx / (hist.length - 1)) * w;
                const y = h - (p.avgT / maxT) * (h - 20) - 10;
                if (idx === 0) ctx.moveTo(x, y);
                else ctx.lineTo(x, y);
              });
              ctx.stroke();
            }

            async function togglePlay() {
              await fetch("/api/play");
              await fetchState();
              schedulePoll();
            }

            async function stepSim(count) {
              await fetch(`/api/step?count=${count}`);
              await fetchState();
              schedulePoll();
            }

            async function resetSim() {
              lastChartTick = -1;
              await fetch("/api/reset");
              await fetchState();
              schedulePoll();
            }

            async function loadPreset(name) {
              lastChartTick = -1;
              await fetch(`/api/load-preset?name=${name}`);
              await fetchState();
              schedulePoll();
            }

            async function setTile(x, y, type) {
              await fetch(`/api/set-tile?x=${x}&y=${y}&type=${type}`);
              await fetchState();
              schedulePoll();
            }

            async function changeTier(tier) {
              await fetch(`/api/set-tier?tier=${tier}`);
              await fetchState();
              schedulePoll();
            }

            window.addEventListener("keydown", (e) => {
              if (e.target && (e.target.tagName === "INPUT" || e.target.tagName === "SELECT")) return;
              if (e.key === "Escape" || e.key === "i" || e.key === "I") {
                setInteractionMode("INSPECT");
              } else if (e.key === "p" || e.key === "P") {
                setInteractionMode("PAINT");
              }
            });

            initTurbineSelects();
            initPalette();
            setInteractionMode("INSPECT");
            fetchState().then(() => schedulePoll());
            </script>
            </body>
            </html>
            """;
    }
}
