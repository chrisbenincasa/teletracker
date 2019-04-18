import {
  createStyles,
  Link,
  Theme,
  Typography,
  withStyles,
  WithStyles,
} from '@material-ui/core';
import Breadcrumbs from '@material-ui/lab/Breadcrumbs';
import * as R from 'ramda';
import React, { Component } from 'react';
import { connect } from 'react-redux';
import {
  Link as RouterLink,
  Redirect,
  Route,
  RouteComponentProps,
  withRouter,
} from 'react-router-dom';
import withUser, { WithUserProps } from '../../components/withUser';
import { AppState } from '../../reducers';
import { layoutStyles } from '../../styles';
import { List } from '../../types';

const styles = (theme: Theme) =>
  createStyles({
    layout: layoutStyles(theme),
  });

interface OwnProps {
  isAuthed?: boolean;
}

interface RouteParams {
  id: string;
}

type Props = OwnProps &
  RouteComponentProps<RouteParams> &
  WithStyles<typeof styles> &
  WithUserProps;

const breadcrumbNameMap = {
  '/lists': 'Lists',
};

interface BreadcrumbsProps {
  lookup: object;
  onNotFound?: (path: string, isLast: boolean) => string;
}

class CustomBreadcrumbs extends Component<BreadcrumbsProps> {
  render() {
    return (
      <Route>
        {({ location }) => {
          const pathnames = location.pathname.split('/').filter(x => x);

          return (
            <Breadcrumbs arial-label="Breadcrumb">
              <Link
                component={props => <RouterLink {...props} to="/" />}
                color="inherit"
              >
                Home
              </Link>
              {pathnames.map((_, index) => {
                const last = index === pathnames.length - 1;
                const to = `/${pathnames.slice(0, index + 1).join('/')}`;

                let name: string | undefined = this.props.lookup[to];

                if (!name && this.props.onNotFound) {
                  name = this.props.onNotFound(to, last);
                }

                return last ? (
                  <Typography color="textPrimary" key={to}>
                    {name}
                  </Typography>
                ) : (
                  <Link
                    component={props => <RouterLink {...props} to={to} />}
                    color="inherit"
                    key={to}
                  >
                    {name}
                  </Link>
                );
              })}
            </Breadcrumbs>
          );
        }}
      </Route>
    );
  }
}

class ListDetail extends Component<Props> {
  render() {
    let { classes, userSelf } = this.props;

    if (userSelf) {
      let list = R.find<List>(
        R.propEq('id', Number(this.props.match.params.id)),
      )(userSelf.lists);

      if (!list) {
        return <Redirect to="/lists" />;
      } else {
        return (
          <div className={classes.layout}>
            <CustomBreadcrumbs
              lookup={breadcrumbNameMap}
              onNotFound={() => list!.name}
            />
            <Typography component="h1" variant="h4" align="left">
              {list.name}
            </Typography>
          </div>
        );
      }
    } else {
      return null;
    }
  }
}

const mapStateToProps: (appState: AppState) => OwnProps = appState => {
  return {
    isAuthed: !R.isNil(R.path(['auth', 'token'], appState)),
  };
};

export default withUser(
  withStyles(styles)(withRouter(connect(mapStateToProps)(ListDetail))),
);
