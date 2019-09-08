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
import { parseInitials } from '../utils/textHelper';
import RouterLink from './RouterLink';
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

  renderAvatar(person: CastMember) {
    let { classes } = this.props;

    return (
      <RouterLink to={'/person/' + person.slug} className={classes.avatarLink}>
        <Avatar
          alt={person.name}
          src={
            person.profilePath
              ? `https://image.tmdb.org/t/p/w185/${person.profilePath}`
              : ''
          }
          className={classes.avatar}
        >
          {person.profilePath ? null : parseInitials(person.name!, 'name')}
        </Avatar>
        <Typography
          variant="subtitle1"
          color="textPrimary"
          className={classes.actualName}
          align="center"
        >
          {person.name}
        </Typography>
        <Typography
          variant="subtitle2"
          color="textPrimary"
          className={classes.characterName}
          align="center"
        >
          {person.characterName}
        </Typography>
      </RouterLink>
    );
  }

  render() {
    const { classes, itemDetail } = this.props;
    const credits = itemDetail.cast;

    return credits && credits.length > 0 ? (
      <React.Fragment>
        <div className={classes.castContainer}>
          <Typography color="inherit" variant="h5">
            Cast
          </Typography>

          <Grid container className={classes.grid}>
            {credits.map(person => (
              <div className={classes.personContainer} key={person.id}>
                {this.renderAvatar(person)}
              </div>
            ))}
          </Grid>
        </div>
      </React.Fragment>
    ) : null;
  }
}

export default withStyles(styles)(Cast);
