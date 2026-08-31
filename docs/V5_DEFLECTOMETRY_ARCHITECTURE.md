# V5 Deflectometry Architecture & Geometric Correspondence Engine

## Measurement Principle
Smartphone deflectometry measures spectacle lens refractive properties (sphere, cylinder, axis) by observing the distortion of a known reference dot pattern viewed through the test lens.
- **Reference Image**: Captured without lens.
- **Observed Image**: Captured through spectacle lens.
- **Core Challenge**: Establishing robust point correspondences between reference points and lens-distorted points without relying on brittle global integer `(row, column)` topology assignment.

## V4 Limitation
V4 relied on strict grid topology assignment (`row, column`), which failed when lens deformation or tilt caused missing points, collisions, or disconnected components, resulting in very low match counts (~5 matches out of 26 reference points).

## V5 Architecture & Stages
1. **Point Cloud Detection**: Extracts dot features using robust contour analysis and filtering.
2. **Normalized Local Descriptors**: Computes rotation-and-scale tolerant local neighborhood features (normalized distances, relative angles, density).
3. **Robust Spacing Estimation**: Computes robust median target spacing for reference and lens point clouds independently.
4. **Hypothesis Generation & Seed Matches**: Finds high-confidence seed correspondences via mutual nearest neighbor search in descriptor space.
5. **Coarse Global Prediction**: Estimates robust similarity transform (translation, rotation, scale) using RANSAC on seed matches.
6. **Mutual Nearest Neighbor Expansion**: Predicts unmatched point positions using adaptive search radius (`medianSpacing * factor`) with strict one-to-one mutual verification.
7. **Local Consistency & Iterative Refinement**: Validates vector smoothness and local neighbor ordering.
8. **Raw Field & Deformation Analysis**: Extracts uncalibrated displacement vectors, Jacobian matrices, symmetric strain components, and principal deformation components ($\lambda_1, \lambda_2$).

## Optical Signal Preservation Rule
- Similarity transforms and coarse predictions are used *strictly* for match prediction.
- Original reference and observed points (and raw displacement vectors $\Delta x, \Delta y$) are preserved without permanently normalizing away scale or anisotropic deformation.

## Synthetic Test Suite & Telemetry
V5 includes a comprehensive test suite (Tests 1–15) validating robustness against translation, rotation, isotropic scale, anisotropic deformation, missing points, false detections, and positional noise.
