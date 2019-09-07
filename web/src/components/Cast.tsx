import {
  Avatar,
  createStyles,
  Grid,
  Theme,
  Typography,
  WithStyles,
  withStyles,
} from '@material-ui/core';
import { CastMember } from '../types';
import React, { Component } from 'react';
import { getMetadataPath } from '../utils/metadata-access';
import { parseInitials } from '../utils/textHelper';
import RouterLink from './RouterLink';
import * as R from 'ramda';
import _ from 'lodash';
import { Person } from 'themoviedb-client-typed';
import Thing from '../types/Thing';

const styles = (theme: Theme) =>
  createStyles({
    avatar: {
      width: 100,
      height: 100,
      boxShadow: '7px 10px 23px -8px rgba(0,0,0,0.57)',
      '&:hover': {
        opacity: 0.8,
      },
    },
    avatarLink: {
      textDecoration: 'none',
    },
    actualName: {
      fontWeight: 'bold',
    },
    castContainer: {
      marginTop: 10,
    },
    characterName: {
      fontStyle: 'Italic',
    },
    grid: {
      [theme.breakpoints.up('sm')]: {
        justifyContent: 'flex-start',
      },
      justifyContent: 'space-around',
    },
    personContainer: {
      display: 'flex',
      flexDirection: 'column',
      maxWidth: 100,
      margin: 10,
    },
  });

interface OwnProps {
  itemDetail: Thing;
}

type Props = OwnProps & WithStyles<typeof styles>;

class Cast extends Component<Props, {}> {
  constructor(props: Props) {
    super(props);
  }

  renderAvatar(person: Person, memberById: { [id: string]: CastMember }) {
    let { classes } = this.props;

    let avatar = (
      <Avatar
        alt={person.name}
        src={
          person.profile_path
            ? `https://image.tmdb.org/t/p/w185/${person.profile_path}`
            : ''
        }
        className={classes.avatar}
      >
        {person.profile_path ? null : parseInitials(person.name!, 'name')}
      </Avatar>
    );

    let dbPerson = memberById[person.id.toString()];

    if (dbPerson) {
      return (
        <RouterLink
          to={'/person/' + dbPerson.slug}
          className={classes.avatarLink}
        >
          {avatar}
        </RouterLink>
      );
    } else {
      return avatar;
    }
  }

  render() {
    const { classes, itemDetail } = this.props;
    const credits = Object(getMetadataPath(itemDetail, 'credits'));

    let castByTmdbId = R.mapObjIndexed(
      (c: CastMember[]) => R.head(c)!,
      R.groupBy(
        (c: CastMember) => c.tmdbId!,
        R.filter(c => _.negate(_.isUndefined)(c.tmdbId), itemDetail.cast || []),
      ),
    );

    return credits && credits.cast && credits.cast.length > 0 ? (
      <React.Fragment>
        <div className={classes.castContainer}>
          <Typography color="inherit" variant="h5">
            Cast
          </Typography>

          <Grid container className={classes.grid}>
            {credits.cast.map(person => (
              <div className={classes.personContainer} key={person.id}>
                {this.renderAvatar(person, castByTmdbId)}
                <Typography
                  variant="subtitle1"
                  color="inherit"
                  className={classes.actualName}
                  align="center"
                >
                  {person.name}
                </Typography>
                <Typography
                  variant="subtitle2"
                  color="inherit"
                  className={classes.characterName}
                  align="center"
                >
                  {person.character}
                </Typography>
              </div>
            ))}
          </Grid>
        </div>
      </React.Fragment>
    ) : null;
  }
}

export default withStyles(styles)(Cast);
