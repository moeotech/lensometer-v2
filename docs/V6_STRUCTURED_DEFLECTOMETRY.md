# V6 Structured Deflectometry

## Why V6 Exists
Our previous iterations (V4 and V5) utilized generic point detection, generic local-descriptor matching, and mutual correspondence algorithms to infer grid structures and calculate global deformation Jacobians. While robust for general geometric matching, these methods lack the stability and precise spatial anchoring required for optical lensometry (especially compared to systems like the open-source EyeNetra architecture).

V6 introduces **Structured Deflectometry**, a clean-room architectural overhaul that entirely discards blind point matching. Instead of treating target dots as anonymous points, V6 relies on a explicitly known target definition. By understanding the geometric grid prior to capture, V6 dramatically reduces matching ambiguity, allows for robust anchor recovery, and paves the way for strict calibration models.

## Comparison: V4 / V5 vs V6

| Feature | V4 / V5 (Generic) | V6 (Structured) |
|---|---|---|
| **Grid Topology** | Inferred blindly from generic points | Predefined explicit model (`V6TargetDefinition`) |
| **Correspondence** | Pairwise matching via local descriptors | Direct lookup via predicted spatial positions |
| **Resilience** | Susceptible to global spatial warp | Predictable local neighbor searches |
| **Output Metric** | Deformation Jacobians / Eigenvalues | Predefined Directional Neighbor Radius Ratios |
| **Calibration** | Global linear mappings | Local directional ratio-to-diopter curves |

## Architecture Overview

### 1. Anchors (Phase 2 & 3)
A structured grid requires identification of its center and orientation. In V6, the `V6GridDetector` estimates the geometric center and median spacing of the detected points. Future iterations will support explicit hardware anchors (e.g. specialized center dot, distinct quadrant markers) to precisely lock rotation, scale, and grid indexing. Once locked, every grid cell $(row, col)$ has a predicted location.

### 2. Known-Grid Architecture
By knowing the expected position of a cell $(r, c)$, we restrict our search for the observed feature to a tiny localized radius (`maxSearchRadius = medianSpacing * 0.4`). If a dot is missing or obscured by noise, the cell is marked invalid, but the global grid structure remains perfectly intact. This completely eliminates the "generic correspondence problem" from V5.

### 3. Predefined Neighbor Vectors
Instead of calculating a global Jacobian, we define specific pairs of cells (e.g. center cell $(10, 10)$ to neighbor cell $(10, 12)$).
For every predefined pair:
- **Reference (Zero):** Distance $r_0$
- **Lens:** Distance $r_1$
- **Radius Ratio:** $r_1 / r_0$
- **Meridian Angle:** $\theta$

Only vectors where all participating cells are valid in both reference and lens captures are utilized. This gives us robust, distributed directional signals.

### 4. Calibration Engine
V6 establishes the software scaffolding for future physical calibration. The `V6PowerCalibration` module will map experimental radius ratios to true directional optical power (Diopters) using actual measurements from standard trial lenses. **Currently, the system outputs UNCALIBRATED experimental ratios and avoids asserting fake SPH/CYL.**

### 5. Sinusoidal Power Model
Directional measurements are fed into `V6SinusoidalFitter`. We implement an independent least-squares fitting of the second-harmonic sinusoidal model:
$P(\theta) = A_0 + A_{\text{cos}} \cos(2\theta) + A_{\text{sin}} \sin(2\theta)$

From this fit, we extract:
- Mean power (spherical equivalent proxy)
- Astigmatic amplitude
- Principal orientation axis

### 6. Hardware Geometry (`V6DeviceGeometry`)
Physical accuracy requires physical constants. V6 explicitly tracks:
- `cameraToLensDistanceMm`
- `lensToTargetDistanceMm`
- `targetDotSpacingMm`
These constants will be utilized in later milestones to build the theoretical ray-tracing bounds of the system.

## Validation Plan

1. **Test Suite Verification:**
   - Synthetic grids simulating isotropic (spherical) scaling
   - Synthetic grids simulating anisotropic (cylindrical) scaling
   - Missing dot resilience
2. **Physical "Raw Mode" Milestone:**
   - Achieve stable recovery of Reference and Lens grids simultaneously.
   - Produce valid, repeatable directional neighbor-vector radius ratios.
3. **Calibration Integration:**
   - Introduce physical trial lenses, record ratios, and generate device-specific `V6PowerCalibration` curves.
