# -*- coding: utf-8 -*-

import pytest
import requests

from verta.environment import Python


pytestmark = pytest.mark.not_oss  # skip if run in oss setup. Applied to entire module


class TestFromRun:
    def test_from_run(self, experiment_run, model_for_deployment, registered_model):
        np = pytest.importorskip("numpy")

        experiment_run.log_model(model_for_deployment["model"], custom_modules=[])
        experiment_run.log_environment(Python(["scikit-learn"]))

        artifact = np.random.random((36, 12))
        experiment_run.log_artifact("some-artifact", artifact)

        for i, run_id_arg in enumerate(
            [experiment_run.id, experiment_run]
        ):  # also accept run obj
            model_version = registered_model.create_version_from_run(
                run_id=run_id_arg,
                name="From Run {} {}".format(experiment_run.id, i),
            )

            env_str = str(model_version.get_environment())
            assert "scikit-learn" in env_str
            assert "Python" in env_str

            assert (
                model_for_deployment["model"].get_params()
                == model_version.get_model().get_params()
            )
            assert np.array_equal(model_version.get_artifact("some-artifact"), artifact)

    def test_experiment_run_id_property(self, experiment_run, registered_model):
        """Verify ``ModelVersion.experiment_run_id`` value."""
        model_version = registered_model.create_version()
        assert model_version.experiment_run_id is None

        model_version_from_run = registered_model.create_version_from_run(
            experiment_run,
        )
        assert model_version_from_run.experiment_run_id == experiment_run.id

    def test_from_run_diff_workspaces(
        self, client, experiment_run, workspace, created_entities
    ):
        registered_model = client.create_registered_model(workspace=workspace.name)
        created_entities.append(registered_model)

        model_version = registered_model.create_version_from_run(
            run_id=experiment_run.id, name="From Run {}".format(experiment_run.id)
        )

        assert model_version.workspace != experiment_run.workspace

    def test_from_run_diff_workspaces_no_access_error(
        self, client_2, created_entities, workspace, workspace2, client
    ):
        proj = client.set_project(workspace=workspace.name)
        client.set_experiment()
        run = client.set_experiment_run()
        created_entities.append(proj)
        registered_model = client_2.create_registered_model(workspace=workspace2.name)
        created_entities.append(registered_model)

        with pytest.raises(requests.HTTPError) as excinfo:
            registered_model.create_version_from_run(
                run_id=run.id, name="From Run {}".format(run.id)
            )

        exc_msg = str(excinfo.value).strip()
        assert exc_msg.startswith("404")
        assert "not found" in exc_msg
